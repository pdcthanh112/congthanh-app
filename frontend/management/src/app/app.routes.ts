import { Routes } from '@angular/router';
import { adminGuard } from './core/admin.guard';
import { ForbiddenComponent } from './core/forbidden.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'catalog/attributes' },
  {
    path: 'catalog',
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'attributes' },
      {
        path: 'attributes',
        title: 'Thuộc tính',
        loadComponent: () => import('./catalog/attributes-page.component').then((m) => m.AttributesPageComponent),
      },
      {
        path: 'groups',
        title: 'Nhóm hiển thị',
        loadComponent: () => import('./catalog/groups-page.component').then((m) => m.GroupsPageComponent),
      },
      {
        path: 'templates',
        title: 'Mẫu theo danh mục',
        loadComponent: () => import('./catalog/templates-page.component').then((m) => m.TemplatesPageComponent),
      },
      {
        path: 'specifications',
        title: 'Thông số tự do',
        loadComponent: () =>
          import('./catalog/specifications-page.component').then((m) => m.SpecificationsPageComponent),
      },
    ],
  },
  { path: 'forbidden', component: ForbiddenComponent },
  { path: '**', redirectTo: 'catalog/attributes' },
];
