import { initFederation } from '@angular-architects/native-federation';

// No remotes registered at startup on purpose: the portfolio must open instantly even when the demo cluster is
// switched off. Each micro frontend's remoteEntry.json is fetched only when someone opens the demo (app.routes.ts).
initFederation(
  {},
  {
    hostRemoteEntry: { url: './remoteEntry.json' },
  },
)
  .catch((err) => console.error(err))
  .then((_) => import('./bootstrap'))
  .catch((err) => console.error(err));
