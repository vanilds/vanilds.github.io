import {
  withNativeFederation,
  fromPackageJson,
  mappingsFromWorkspace,
} from '@angular-architects/native-federation/config';

export default withNativeFederation({
  name: 'mfe-customers',

  exposes: {
    // The shell mounts these routes under /demo/…
    './routes': './projects/mfe-customers/src/app/app.routes.ts',
  },

  shared: fromPackageJson({
    singleton: true,
    strictVersion: true,
    requiredVersion: 'auto',
    build: 'package',
  })
    .patch(['@angular/*'], { includeSecondaries: { keepAll: true } })
    .get(),

  // @creditdesk/shared (tsconfig path) is loaded once for all apps: one Session, one I18n, one HttpClient setup
  sharedMappings: mappingsFromWorkspace({ singleton: true }).filter(['@creditdesk/shared']).get(),

  skip: ['rxjs/ajax', 'rxjs/fetch', 'rxjs/testing', 'rxjs/webSocket'],

  features: {
    denseChunking: true,
    denseExternals: true,
    strictImports: false,
  },
});
