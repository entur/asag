variable "labels" {
  description = "Labels used in all resources"
  type = map(string)
  default = {
    manager = "terraform"
    team = "ror"
    slack = "talk-ror"
    app = "asag"
  }
}
variable "kube_namespace" {
  description = "The Kubernetes namespace"
}

variable "load_config_file" {
  description = "Do not load kube config file"
  default = false
}
variable "gcp_project" {
  description = "The GCP project id"
}
variable "ror-asag-mapbox-user" {
  description = "ror asag mapbox user name"
}

variable "ror-asag-mapbox-access-token" {
  description = "ror asag mapbox access token"
}

variable "asag_storage_bucket" {
  description = "asag storage bucket"
}
variable "service_account_bucket_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/storage/docs/access-control/iam-roles"
  default = "roles/storage.objectViewer"
}