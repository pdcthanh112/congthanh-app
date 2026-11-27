import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { APP_CONFIG } from './config';

/** Gắn Bearer token cho lời gọi tới API của mình (cùng origin, qua gateway); request khác giữ nguyên. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const ours = req.url.startsWith(APP_CONFIG.apiBase) || req.url.startsWith(APP_CONFIG.graphqlUrl);
  if (!ours) return next(req);
  const auth = inject(AuthService);
  return from(auth.token()).pipe(
    switchMap((token) => next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req))
  );
};
