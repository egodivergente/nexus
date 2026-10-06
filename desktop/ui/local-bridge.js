// Browser host for the private loopback edition. The native WebView2 bridge takes precedence.
if (!window.chrome?.webview) {
  const listeners = new Set();
  let session;
  window.chrome = window.chrome || {};
  window.chrome.webview = {
    addEventListener(name, callback) { if (name === 'message') listeners.add(callback); },
    async postMessage(message) {
      let reply;
      try {
        session ||= fetch('/session', {cache: 'no-store'}).then(async response => {
          if (!response.ok) throw Error('No se pudo abrir la sesión local.');
          return (await response.json()).token;
        });
        const token = await session;
        const response = await fetch('/rpc', {
          method: 'POST',
          headers: {'Content-Type': 'application/json', 'X-Nexus-Session': token},
          body: JSON.stringify(message),
          cache: 'no-store'
        });
        reply = await response.json();
      } catch (error) {
        reply = {id: message.id, ok: false, error: error.message || 'No se pudo conectar con Nexus local.'};
      }
      for (const callback of listeners) callback({data: reply});
    }
  };
}
