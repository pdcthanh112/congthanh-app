//tách riêng ra utils cho client và server bởi vì có những function (ví dụ như crypto) chỉ có ở phía server, không có ở phía client (browser)
//nên nếu để chung file utils thì khi chạy, phía client gặp thư viện cryto phải tải thêm thư viện crypto-browserify để thay thế
// => tải dư thừa, làm nặng bundle size ở phía client không cần thiết