// No secrets in here: this file ships to the browser.
export const environment = {
  apiBase: 'http://localhost:8080',
  cvServiceUrl: 'http://localhost:5001',
  jarvisStartUrl: 'https://primary-production-8b38b6c7.up.railway.app/webhook/jarvis-start',
  // never true in production
  useFakeVoice: false,
};
