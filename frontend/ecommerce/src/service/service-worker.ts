self.addEventListener('push', (event) => {
  const data = event.data.json();
  self.registration.showNotification('Order Update', {
    body: data.message,
  });
});
