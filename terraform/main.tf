terraform {
  required_version = ">= 0.12"
}

provider "google" {
  version = "~> 2.19"
}
provider "kubernetes" {
  version = "~> 1.13.3"
  load_config_file = var.load_config_file
}

# Create Service account and secretes
resource "google_service_account" "asag_service_account" {
  account_id = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_project
}

resource "google_storage_bucket_iam_member" "asag_storage_iam_member" {
  bucket = var.asag_storage_bucket
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.asag_service_account.email}"
}

resource "google_service_account_key" "asag_service_account_key" {
  service_account_id = google_service_account.asag_service_account.name
}

resource "kubernetes_secret" "asag_service_account_credentials" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "credentials.json" = base64decode(google_service_account_key.asag_service_account_key.private_key)
  }
}

resource "kubernetes_secret" "ror-asag-secrets" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secrets"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "ror-asag-mapbox-user" = var.ror-asag-mapbox-user
    "ror-asag-mapbox-access-token" = var.ror-asag-mapbox-access-token
  }
}