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
  default = "asag"
}

variable "ror-asag-mapbox-user" {
  description = "ror asag mapbox user name"
}

variable "ror-asag-mapbox-access-token" {
  description = "ror asag mapbox access token"
}


variable "ror-slack-url" {
  description = "slack notification url"
}