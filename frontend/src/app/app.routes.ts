import { Routes } from '@angular/router';
import { adminGuard } from './features/admin/admin.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/storefront/storefront.component').then((m) => m.StorefrontComponent),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/identity/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'cart',
    loadComponent: () => import('./features/cart/cart.component').then((m) => m.CartComponent),
  },
  {
    path: 'products/:slug',
    loadComponent: () =>
      import('./features/storefront/product-detail.component').then(
        (m) => m.ProductDetailComponent,
      ),
  },
  {
    path: 'producers/:slug',
    loadComponent: () =>
      import('./features/storefront/producer-detail.component').then(
        (m) => m.ProducerDetailComponent,
      ),
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
  {
    path: 'admin/inventory',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/admin/inventory/inventory-admin.component').then(
        (m) => m.InventoryAdminComponent,
      ),
  },
];
