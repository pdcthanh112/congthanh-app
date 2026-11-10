export const handleAsync = async <T>(promise: Promise<T>): Promise<[T, null] | [null, Error]> => {
  try {
    const data = await promise;
    return [data, null];
  } catch (error) {
    return [null, error instanceof Error ? error : new Error(String(error))];
  }
};

// const [user, error] = await handleAsync(fetchData(1));

// if (error) {
//   console.error('Error:', error.message);
// } else {
//   console.log('User:', user.name);
// }
