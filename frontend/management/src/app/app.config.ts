import { APP_INITIALIZER, ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Đăng nhập Keycloak trước khi dựng app: mọi trang đều cần tài khoản.
    { provide: APP_INITIALIZER, multi: true, deps: [AuthService], useFactory: (auth: AuthService) => () => auth.init() },
  ],
};
