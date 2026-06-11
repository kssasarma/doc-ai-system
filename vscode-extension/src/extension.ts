import * as vscode from 'vscode';
import { DocsPanelProvider } from './panel';

let panelProvider: DocsPanelProvider;

export function activate(context: vscode.ExtensionContext): void {
  panelProvider = new DocsPanelProvider(context.extensionUri);

  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(DocsPanelProvider.viewType, panelProvider, {
      webviewOptions: { retainContextWhenHidden: true },
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('docsinator.openPanel', () => {
      vscode.commands.executeCommand('docsinator.chatView.focus');
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('docsinator.askSelection', () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) return;
      const selection = editor.document.getText(editor.selection).trim();
      if (!selection) {
        vscode.window.showInformationMessage('Docs-inator: Select some text first.');
        return;
      }
      vscode.commands.executeCommand('docsinator.chatView.focus');
      // Brief delay to ensure the webview is ready
      setTimeout(() => panelProvider.askQuestion(selection), 200);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('docsinator.configure', async () => {
      const apiUrl = await vscode.window.showInputBox({
        prompt: 'Docs-inator API URL (e.g. https://docs.example.com)',
        value: vscode.workspace.getConfiguration('docsinator').get<string>('apiUrl', ''),
        ignoreFocusOut: true,
      });
      if (apiUrl === undefined) return;

      const apiKey = await vscode.window.showInputBox({
        prompt: 'API Key (from Settings → API Keys in the Docs-inator web app)',
        value: vscode.workspace.getConfiguration('docsinator').get<string>('apiKey', ''),
        password: true,
        ignoreFocusOut: true,
      });
      if (apiKey === undefined) return;

      const cfg = vscode.workspace.getConfiguration('docsinator');
      await cfg.update('apiUrl', apiUrl.replace(/\/$/, ''), vscode.ConfigurationTarget.Global);
      await cfg.update('apiKey', apiKey, vscode.ConfigurationTarget.Global);

      vscode.window.showInformationMessage('Docs-inator: Configuration saved.');
    })
  );
}

export function deactivate(): void {}
