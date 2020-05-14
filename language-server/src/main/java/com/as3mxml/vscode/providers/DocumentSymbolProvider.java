/*
Copyright 2016-2020 Bowler Hat LLC

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
package com.as3mxml.vscode.providers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.WorkspaceFolderManager;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class DocumentSymbolProvider
{
	private WorkspaceFolderManager workspaceFolderManager;
	private boolean hierarchicalDocumentSymbolSupport;

	public DocumentSymbolProvider(WorkspaceFolderManager workspaceFolderManager, boolean hierarchicalDocumentSymbolSupport)
	{
		this.workspaceFolderManager = workspaceFolderManager;
		this.hierarchicalDocumentSymbolSupport = hierarchicalDocumentSymbolSupport;
	}

	public List<Either<SymbolInformation, DocumentSymbol>> documentSymbol(DocumentSymbolParams params, CancelChecker cancelToken)
	{
		cancelToken.checkCanceled();
		TextDocumentIdentifier textDocument = params.getTextDocument();
		Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
		if (path == null)
		{
			cancelToken.checkCanceled();
			return Collections.emptyList();
		}
		WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
		if(folderData == null || folderData.project == null)
		{
			cancelToken.checkCanceled();
			return Collections.emptyList();
		}
		ILspProject project = folderData.project;

		ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
		if (unit == null)
		{
			cancelToken.checkCanceled();
			//we couldn't find a compilation unit with the specified path
			return Collections.emptyList();
		}

		IASScope[] scopes;
		try
		{
			scopes = unit.getFileScopeRequest().get().getScopes();
		}
		catch (Exception e)
		{
			cancelToken.checkCanceled();
			return Collections.emptyList();
		}
		List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
		if (hierarchicalDocumentSymbolSupport)
		{
			List<DocumentSymbol> symbols = new ArrayList<>();
			for (IASScope scope : scopes)
			{
				scopeToDocumentSymbols(scope, project, symbols);
			}
			for (DocumentSymbol symbol : symbols)
			{
				result.add(Either.forRight(symbol));
			}
		}
		else //fallback to non-hierarchical
		{
			List<SymbolInformation> symbols = new ArrayList<>();
			for (IASScope scope : scopes)
			{
				scopeToSymbolInformation(scope, project, symbols);
			}
			for (SymbolInformation symbol : symbols)
			{
				result.add(Either.forLeft(symbol));
			}
		}
		cancelToken.checkCanceled();
		return result;
	}

    private void scopeToSymbolInformation(IASScope scope, ILspProject project, List<SymbolInformation> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                scopeToSymbolInformation(packageScope, project, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                IASScope typeScope = typeDefinition.getContainedScope();
                if (!definition.isImplicit())
                {
                    SymbolInformation typeSymbol = workspaceFolderManager.definitionToSymbolInformation(typeDefinition, project);
                    result.add(typeSymbol);
                }
                scopeToSymbolInformation(typeScope, project, result);
                
            }
            else if (definition instanceof IFunctionDefinition
                    || definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                SymbolInformation localSymbol = workspaceFolderManager.definitionToSymbolInformation(definition, project);
                if (localSymbol != null)
                {
                    result.add(localSymbol);
                }
            }
        }
    }

    private void scopeToDocumentSymbols(IASScope scope, ILspProject project, List<DocumentSymbol> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                scopeToDocumentSymbols(packageScope, project, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                IASScope typeScope = typeDefinition.getContainedScope();
                List<DocumentSymbol> childSymbols = new ArrayList<>();
                scopeToDocumentSymbols(typeScope, project, childSymbols);

                if (definition.isImplicit())
                {
                    result.addAll(childSymbols);
                }
                else
                {
                    DocumentSymbol typeSymbol = workspaceFolderManager.definitionToDocumentSymbol(typeDefinition, project);
                    if (typeSymbol == null)
                    {
                        result.addAll(childSymbols);
                    }
                    else
                    {
                        typeSymbol.setChildren(childSymbols);
                        result.add(typeSymbol);
                    }
                }
                
            }
            else if (definition instanceof IFunctionDefinition
                    || definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                DocumentSymbol localSymbol = workspaceFolderManager.definitionToDocumentSymbol(definition, project);
                if (localSymbol != null)
                {
                    result.add(localSymbol);
                }
            }
        }
    }
}