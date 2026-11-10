import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import QuantitySelector from './QuantitySelector';

describe('QuantitySelector', () => {
  const defaultProps = {
    initialValue: 5,
    min: 1,
    max: 10,
    onChange: jest.fn(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders with correct initial value', () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    expect(input).toHaveValue(5);
  });

  test('disables decrease button when quantity equals min', () => {
    render(<QuantitySelector {...defaultProps} initialValue={1} min={1} />);
    const decreaseButton = screen.getByLabelText('Decrease quantity');
    expect(decreaseButton).toBeDisabled();
  });

  test('disables increase button when quantity equals max', () => {
    render(<QuantitySelector {...defaultProps} initialValue={10} max={10} />);
    const increaseButton = screen.getByLabelText('Increase quantity');
    expect(increaseButton).toBeDisabled();
  });

  test('increases quantity when increase button is clicked', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const increaseButton = screen.getByLabelText('Increase quantity');
    await userEvent.click(increaseButton);
    expect(defaultProps.onChange).toHaveBeenCalledWith(6);
    expect(screen.getByLabelText('Số lượng')).toHaveValue(6);
  });

  test('decreases quantity when decrease button is clicked', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const decreaseButton = screen.getByLabelText('Decrease quantity');
    await userEvent.click(decreaseButton);
    expect(defaultProps.onChange).toHaveBeenCalledWith(4);
    expect(screen.getByLabelText('Số lượng')).toHaveValue(4);
  });

  test('does not decrease below min', async () => {
    render(<QuantitySelector {...defaultProps} initialValue={1} min={1} />);
    const decreaseButton = screen.getByLabelText('Decrease quantity');
    await userEvent.click(decreaseButton);
    expect(defaultProps.onChange).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Số lượng')).toHaveValue(1);
  });

  test('does not increase above max', async () => {
    render(<QuantitySelector {...defaultProps} initialValue={10} max={10} />);
    const increaseButton = screen.getByLabelText('Increase quantity');
    await userEvent.click(increaseButton);
    expect(defaultProps.onChange).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Số lượng')).toHaveValue(10);
  });

  test('updates quantity when valid input is entered', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    await userEvent.clear(input);
    await userEvent.type(input, '7');
    expect(defaultProps.onChange).toHaveBeenCalledWith(7);
    expect(input).toHaveValue(7);
  });

  test('sets quantity to min when input is below min', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    await userEvent.clear(input);
    await userEvent.type(input, '0');
    expect(defaultProps.onChange).toHaveBeenCalledWith(1);
    expect(input).toHaveValue(1);
  });

  test('sets quantity to max when input is above max', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    await userEvent.clear(input);
    await userEvent.type(input, '15');
    expect(defaultProps.onChange).toHaveBeenCalledWith(10);
    expect(input).toHaveValue(10);
  });

  test('adjusts quantity to min on blur if below min', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    await userEvent.clear(input);
    await userEvent.type(input, '0');
    await userEvent.tab(); // Trigger blur
    expect(defaultProps.onChange).toHaveBeenLastCalledWith(1);
    expect(input).toHaveValue(1);
  });

  test('adjusts quantity to max on blur if above max', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    await userEvent.clear(input);
    await userEvent.type(input, '15');
    await userEvent.tab(); // Trigger blur
    expect(defaultProps.onChange).toHaveBeenLastCalledWith(10);
    expect(input).toHaveValue(10);
  });

  test('updates quantity when initialValue changes', () => {
    const { rerender } = render(<QuantitySelector {...defaultProps} />);
    rerender(<QuantitySelector {...defaultProps} initialValue={8} />);
    expect(screen.getByLabelText('Số lượng')).toHaveValue(8);
  });

  test('handles non-numeric input gracefully', async () => {
    render(<QuantitySelector {...defaultProps} />);
    const input = screen.getByLabelText('Số lượng');
    await userEvent.clear(input);
    await userEvent.type(input, 'abc');
    expect(defaultProps.onChange).toHaveBeenCalledWith(1);
    expect(input).toHaveValue(1);
  });

  test('renders with correct ARIA attributes', () => {
    render(<QuantitySelector {...defaultProps} />);
    const decreaseButton = screen.getByLabelText('Decrease quantity');
    const increaseButton = screen.getByLabelText('Increase quantity');
    const input = screen.getByLabelText('Số lượng');

    expect(decreaseButton).toHaveAttribute('aria-controls', 'quantity-input');
    expect(increaseButton).toHaveAttribute('aria-controls', 'quantity-input');
    expect(input).toHaveAttribute('aria-label', 'Số lượng');
  });
});