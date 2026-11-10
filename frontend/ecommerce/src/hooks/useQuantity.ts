import { useState } from 'react';

const useCounter = () => {
  const [count, setCount] = useState<number>(0);

  const onIncrease = () => setCount(count + 1);
  const onDecrease = () => setCount(count - 1);

  return [count, onIncrease, onDecrease];
};
