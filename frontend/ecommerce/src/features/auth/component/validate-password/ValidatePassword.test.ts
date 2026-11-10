import { render, screen } from '@testing-library/react';
import ValidatePassword from './ValidatePassword';
import { useTranslations } from 'next-intl';

jest.mock('next-intl', () => ({
  useTranslations: jest.fn(),
}));

// jest.mock('@/utils/regex', () => ({
//   lengthRegex: /.{8,}/,
//   lowerCharacterRegex: /[a-z]/,
//   upperCharacterRegex: /[A-Z]/,
//   numberCharacterRegex: /\d/,
//   specialCharacterRegex: /[!@#$%^&*]/,
// }));

describe('ValidatePassword', () => {
  const mockT = jest.fn((key, values) => `${key}${values ? `:${JSON.stringify(values)}` : ''}`);

  beforeEach(() => {
    (useTranslations as jest.Mock).mockReturnValue(mockT);
    jest.clearAllMocks();
  });

  // Test rendering with empty password
  it('renders all validation rules with Ban icons for empty password', () => {
    render(<ValidatePassword password="" />);

    const banIcons = screen.getAllByTestId('ban-icon');
    expect(banIcons).toHaveLength(5);

    expect(screen.getByText('length_require:{"field":"Password"}')).toBeInTheDocument();
    expect(screen.getByText('lowercase_letter_require:{"field":"Password"}')).toBeInTheDocument();
    expect(screen.getByText('uppercase_letter_require:{"field":"Password"}')).toBeInTheDocument();
    expect(screen.getByText('numberic_character_require:{"field":"Password"}')).toBeInTheDocument();
    expect(screen.getByText('special_character_require:{"field":"Password"}')).toBeInTheDocument();

    const containers = screen.getAllByText(/require/).map((el) => el.parentElement);
    containers.forEach((container) => {
      expect(container).not.toHaveClass('text-green-500');
    });
  });

  // Test valid password
  it('shows CheckCheck icons and green text for valid password', () => {
    render(<ValidatePassword password="Abcd123!longenough" />);

    const checkIcons = screen.getAllByTestId('checkcheck-icon');
    expect(checkIcons).toHaveLength(5);

    const containers = screen.getAllByText(/require/).map((el) => el.parentElement);
    containers.forEach((container) => {
      expect(container).toHaveClass('text-green-500');
    });
  });

  // Test partial valid password
  it('shows mixed icons for partially valid password', () => {
    render(<ValidatePassword password="abc123" />);

    // Valid: lowercase, number
    expect(screen.getByText('lowercase_letter_require:{"field":"Password"}').parentElement).toHaveClass(
      'text-green-500'
    );
    expect(screen.getByText('numberic_character_require:{"field":"Password"}').parentElement).toHaveClass(
      'text-green-500'
    );
    expect(screen.getAllByTestId('checkcheck-icon')).toHaveLength(2);

    // Invalid: length, uppercase, special
    expect(screen.getByText('length_require:{"field":"Password"}').parentElement).not.toHaveClass('text-green-500');
    expect(screen.getByText('uppercase_letter_require:{"field":"Password"}').parentElement).not.toHaveClass(
      'text-green-500'
    );
    expect(screen.getByText('special_character_require:{"field":"Password"}').parentElement).not.toHaveClass(
      'text-green-500'
    );
    expect(screen.getAllByTestId('ban-icon')).toHaveLength(3);
  });

  // Test edge cases
  it('handles null or undefined password', () => {
    render(<ValidatePassword password={null as any} />);

    const banIcons = screen.getAllByTestId('ban-icon');
    expect(banIcons).toHaveLength(5);

    const containers = screen.getAllByText(/require/).map((el) => el.parentElement);
    containers.forEach((container) => {
      expect(container).not.toHaveClass('text-green-500');
    });
  });

  // Test internationalization
  it('calls useTranslations with correct namespace', () => {
    render(<ValidatePassword password="" />);

    expect(useTranslations).toHaveBeenCalledWith('auth.validation.password');
    expect(mockT).toHaveBeenCalledWith('length_require', { field: 'Password' });
    expect(mockT).toHaveBeenCalledWith('lowercase_letter_require', { field: 'Password' });
    expect(mockT).toHaveBeenCalledWith('uppercase_letter_require', { field: 'Password' });
    expect(mockT).toHaveBeenCalledWith('numberic_character_require', { field: 'Password' });
    expect(mockT).toHaveBeenCalledWith('special_character_require', { field: 'Password' });
  });

  // Test accessibility
  it('has accessible text for screen readers', () => {
    render(<ValidatePassword password="" />);

    const validationMessages = [
      'length_require:{"field":"Password"}',
      'lowercase_letter_require:{"field":"Password"}',
      'uppercase_letter_require:{"field":"Password"}',
      'numberic_character_require:{"field":"Password"}',
      'special_character_require:{"field":"Password"}',
    ];

    validationMessages.forEach((message) => {
      expect(screen.getByText(message)).toBeVisible();
    });
  });

  // Test dynamic updates
  it('updates validation when password changes', () => {
    const { rerender } = render(<ValidatePassword password="abc" />);

    expect(screen.getByText('lowercase_letter_require:{"field":"Password"}').parentElement).toHaveClass(
      'text-green-500'
    );
    expect(screen.getAllByTestId('ban-icon')).toHaveLength(4);

    rerender(<ValidatePassword password="Abcd123!longenough" />);

    expect(screen.getAllByTestId('checkcheck-icon')).toHaveLength(5);
    const containers = screen.getAllByText(/require/).map((el) => el.parentElement);
    containers.forEach((container) => {
      expect(container).toHaveClass('text-green-500');
    });
  });
});
