// Addresses the app talks to. No keys or secrets belong here: the browser only ever calls
// our own backend and the n8n webhook.
export const environment = {
  apiBase: 'http://localhost:8080',
  jarvisStartUrl: 'https://primary-production-8b38b6c7.up.railway.app/webhook/jarvis-start',
  // simulated voice call for tests and offline demos; never true in production
  useFakeVoice: false,
};
