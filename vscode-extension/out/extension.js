"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const panel_1 = require("./panel");
let panelProvider;
function activate(context) {
    panelProvider = new panel_1.DocsPanelProvider(context.extensionUri);
    context.subscriptions.push(vscode.window.registerWebviewViewProvider(panel_1.DocsPanelProvider.viewType, panelProvider, {
        webviewOptions: { retainContextWhenHidden: true },
    }));
    context.subscriptions.push(vscode.commands.registerCommand('docsinator.openPanel', () => {
        vscode.commands.executeCommand('docsinator.chatView.focus');
    }));
    context.subscriptions.push(vscode.commands.registerCommand('docsinator.askSelection', () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor)
            return;
        const selection = editor.document.getText(editor.selection).trim();
        if (!selection) {
            vscode.window.showInformationMessage('Docs-inator: Select some text first.');
            return;
        }
        vscode.commands.executeCommand('docsinator.chatView.focus');
        // Brief delay to ensure the webview is ready
        setTimeout(() => panelProvider.askQuestion(selection), 200);
    }));
    context.subscriptions.push(vscode.commands.registerCommand('docsinator.configure', async () => {
        const apiUrl = await vscode.window.showInputBox({
            prompt: 'Docs-inator API URL (e.g. https://docs.example.com)',
            value: vscode.workspace.getConfiguration('docsinator').get('apiUrl', ''),
            ignoreFocusOut: true,
        });
        if (apiUrl === undefined)
            return;
        const apiKey = await vscode.window.showInputBox({
            prompt: 'API Key (from Settings → API Keys in the Docs-inator web app)',
            value: vscode.workspace.getConfiguration('docsinator').get('apiKey', ''),
            password: true,
            ignoreFocusOut: true,
        });
        if (apiKey === undefined)
            return;
        const cfg = vscode.workspace.getConfiguration('docsinator');
        await cfg.update('apiUrl', apiUrl.replace(/\/$/, ''), vscode.ConfigurationTarget.Global);
        await cfg.update('apiKey', apiKey, vscode.ConfigurationTarget.Global);
        vscode.window.showInformationMessage('Docs-inator: Configuration saved.');
    }));
}
function deactivate() { }
//# sourceMappingURL=extension.js.map