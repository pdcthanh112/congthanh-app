import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './core/auth.service';

/** AuthService giả: không gọi Keycloak thật. */
function fakeAuth(isAdmin: boolean) {
  return { username: signal('admin1'), isAdmin: signal(isAdmin), logout: jasmine.createSpy('logout') };
}

describe('AppComponent', () => {
  async function render(isAdmin: boolean) {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: fakeAuth(isAdmin) }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ADMIN thấy menu catalog và tên đăng nhập', async () => {
    const el = await render(true);
    expect(el.querySelectorAll('nav a').length).toBe(4);
    expect(el.textContent).toContain('admin1');
  });

  it('không phải ADMIN thì không có menu', async () => {
    const el = await render(false);
    expect(el.querySelector('nav')).toBeNull();
  });
});
