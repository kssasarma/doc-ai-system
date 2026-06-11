// Listens for text selection and sends it to the background for context menu handling.
// No DOM injection — keeps the extension lightweight and permission-minimal.
document.addEventListener('mouseup', () => {
  const selected = window.getSelection()?.toString().trim();
  if (selected && selected.length > 5 && selected.length < 500) {
    chrome.runtime.sendMessage({ type: 'SELECTION', text: selected }).catch(() => {});
  }
});
