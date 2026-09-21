import { Routes } from '@angular/router';
import { adminGuard } from './features/admin/admin.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/identity/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
  },
  {
    path: 'admin/products',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/admin/products/product-admin.component').then(
        (m) => m.ProductAdminComponent,
      ),
  },
];
