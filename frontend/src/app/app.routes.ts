import { Routes } from '@angular/router';
import { adminGuard } from './features/admin/admin.guard';
import { authenticatedGuard } from './features/identity/authenticated.guard';

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
    path: 'register',
    loadComponent: () =>
      import('./features/identity/pages/register/register.component').then(
        (m) => m.RegisterComponent,
      ),
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/identity/pages/verify-email/verify-email.component').then(
        (m) => m.VerifyEmailComponent,
      ),
  },
  {
    path: 'recover-access',
    loadComponent: () =>
      import('./features/identity/pages/recover-access/recover-access.component').then(
        (m) => m.RecoverAccessComponent,
      ),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/identity/pages/reset-password/reset-password.component').then(
        (m) => m.ResetPasswordComponent,
      ),
  },
  {
    path: 'cart',
    loadComponent: () => import('./features/cart/cart.component').then((m) => m.CartComponent),
  },
  {
    path: 'cart/merge',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/cart/cart-merge.component').then((m) => m.CartMergeComponent),
  },
  {
    path: 'checkout',
    loadComponent: () =>
      import('./features/checkout/checkout.component').then((m) => m.CheckoutComponent),
  },
  {
    path: 'orders/:id',
    loadComponent: () =>
      import('./features/checkout/order-progress.component').then((m) => m.OrderProgressComponent),
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
  {
    path: 'admin/coupons',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/admin/coupons/coupon-admin.component').then((m) => m.CouponAdminComponent),
  },
];
