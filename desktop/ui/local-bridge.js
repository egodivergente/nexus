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
        const request = async token => fetch('/rpc', {
          method: 'POST',
          headers: {'Content-Type': 'application/json', 'X-Nexus-Session': token},
          body: JSON.stringify(message),
          cache: 'no-store'
        });
        let response = await request(token);
        // A 403 is rejected before dispatch: renewing this session cannot duplicate a job.
        if (response.status === 403) {
          session = fetch('/session', {cache:'no-store'}).then(async r => {
            if (!r.ok) throw Error('No se pudo renovar la sesión local.');
            return (await r.json()).token;
          });
          response = await request(await session);
        }
        if (!response.ok) throw Error(response.status === 403 ? 'Sesión local rechazada. Vuelve a abrir Nexus.' : 'El servicio local respondió con un error HTTP '+response.status+'. No se ha repetido la operación.');
        reply = await response.json();
      } catch (error) {
        session = null;
        reply = {id: message.id, ok: false, error: error instanceof TypeError ? 'Se ha perdido la conexión con Nexus en este PC. Tu mensaje se conserva. Comprueba la conexión antes de repetir; el envío puede haber llegado al servicio.' : error.message || 'No se pudo conectar con Nexus local.'};
      }
      for (const callback of listeners) callback({data: reply});
    }
  };
}
