import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { DemoRepositoryService } from './core/services/demo-repository.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    /**
     * Seeds the LocalStorage dataset before the first route renders, so every
     * screen finds data already in place. Existing data is never overwritten —
     * only the very first launch seeds.
     */
    provideAppInitializer(() => {
      inject(DemoRepositoryService).initialize();
    }),
  ],
};
