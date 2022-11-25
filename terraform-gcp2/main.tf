# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
}

provider "google" {
  version = ">= 4.26"
}
provider "kubernetes" {
  version = ">= 2.13.1"
}


resource "kubernetes_secret" "ror-asag-secrets" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secrets"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "MAPBOX_USER" = var.ror-asag-mapbox-user
    "MAPBOX_ACCESS_TOKEN" = var.ror-asag-mapbox-access-token
    "HELPER_SLACK_ENDPOINT" = var.ror-slack-url
  }
}