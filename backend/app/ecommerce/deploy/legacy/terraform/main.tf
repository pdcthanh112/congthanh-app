module "networking" {
  source = "./modules/networking"
  # Truyền các biến cần thiết
}

module "databases" {
  source = "./modules/databases"
  # Truyền các biến cần thiết
}

module "kubernetes" {
  source = "./modules/kubernetes"
  # Truyền các biến cần thiết
}

module "logging" {
  source = "./modules/logging"
  # Truyền các biến cần thiết
}

module "monitoring" {
  source = "./modules/monitoring"
  # Truyền các biến cần thiết
}