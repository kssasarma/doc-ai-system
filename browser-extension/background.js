chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'docai-search-selection',
    title: 'Ask Docs-inator: "%s"',
    contexts: ['selection'],
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId !== 'docai-search-selection') return;
  const selectedText = info.selectionText?.trim();
  if (!selectedText) return;

  // Store the selection so the popup can pick it up when it opens
  chrome.storage.local.set({ pendingQuestion: selectedText }, () => {
    chrome.action.openPopup?.().catch(() => {
      // openPopup is not available in all browsers/contexts — the popup will
      // read pendingQuestion from storage when the user opens it manually.
    });
  });
});
