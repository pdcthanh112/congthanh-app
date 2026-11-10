// import React from 'react';
// import useSelectCart from './useSelectCart';

// const CartSelection = () => {
//   const { cart, loading, error, selectedItemId, handleSelectItem } = useSelectCart();

//   if (loading) return <p>Loading...</p>;
//   if (error) return <p>Error :(</p>;

//   return (
//     <div>
//       {cart && cart.map((item) => (
//         <div key={item.id}>
//           <input
//             type="radio"
//             id={item.id}
//             value={item.id}
//             checked={selectedItemId === item.id}
//             onChange={() => handleSelectItem(item.id)}
//           />
//           <label htmlFor={item.id}>{item.name}</label>
//         </div>
//       ))}
//       {selectedItemId && <p>Selected Item ID: {selectedItemId}</p>}
//     </div>
//   );
// };

// export default CartSelection;
