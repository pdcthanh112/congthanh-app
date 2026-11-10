import { render, screen, fireEvent } from '@testing-library/react';
import LoginForm from '../LoginForm'

describe('Login page', () => {
  it('renders login form correctly', () => {
    render(<LoginForm />);

    const usernameInput = screen.getByLabelText('Username');
    const passwordInput = screen.getByLabelText('Password');
    const loginButton = screen.getByRole('button', { name: 'Login' });

    expect(usernameInput).toBeInTheDocument();
    expect(passwordInput).toBeInTheDocument();
    expect(loginButton).toBeInTheDocument();
  });

  it('submits form with valid credentials', async () => {
    render(<LoginPage />);

    const usernameInput = screen.getByLabelText('Username');
    const passwordInput = screen.getByLabelText('Password');
    const loginButton = screen.getByRole('button', { name: 'Login' });

    fireEvent.change(usernameInput, { target: { value: 'your_username' } });
    fireEvent.change(passwordInput, { target: { value: 'your_password' } });

    fireEvent.click(loginButton);

    // Wait for login process, check if successful
    // Example: Check if a success message appears or if user is redirected
    // You may need to mock backend responses or use testing utilities like `mockServiceWorker`

    // Example: Wait for an element indicating login success
    // const successMessage = await screen.findByText('Login successful');
    // expect(successMessage).toBeInTheDocument();
  });
});
