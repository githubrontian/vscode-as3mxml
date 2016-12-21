/*
Copyright 2016 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.nextgenactionscript.vscode;

import java.net.Socket;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Contains the entry point for the JAR.
 */
public class Main
{
    private static final int MISSING_PORT = 100;
    private static final int SERVER_CONNECT_ERROR = 101;

    /**
     * The main entry point when the JAR is run. Opens a socket to communicate
     * with Visual Studio Code using the port specified with the
     * -Dnextgeas.vscode.port command line option. Then, instantiates the
     * ActionScriptLanguageServer, and passes it to the LSP4J library,
     * which handles all of the language server protocol communication.
     * LSP4J calls methods on ActionScriptLanguageServer as requests come in
     * from the text editor.
     */
    public static void main(String[] args)
    {
        String port = System.getProperty("nextgeas.vscode.port");
        if (port == null)
        {
            System.err.println("Error: System property nextgeas.vscode.port is required.");
            System.exit(MISSING_PORT);
        }
        try
        {
            Socket socket = new Socket("localhost", Integer.parseInt(port));

            ActionScriptLanguageServer server = new ActionScriptLanguageServer();
            Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, socket.getInputStream(), socket.getOutputStream());
            server.connect(launcher.getRemoteProxy());
            launcher.startListening();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(SERVER_CONNECT_ERROR);
        }
    }
}