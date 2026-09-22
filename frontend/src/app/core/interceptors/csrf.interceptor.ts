import { HttpInterceptorFn } from '@angular/common/http';

function getXsrfToken(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  const token = getXsrfToken();
  if (token && !['GET', 'HEAD', 'OPTIONS'].includes(req.method.toUpperCase())) {
    const cloned = req.clone({
      setHeaders: { 'X-XSRF-TOKEN': token },
    });
    return next(cloned);
  }
  return next(req);
};
