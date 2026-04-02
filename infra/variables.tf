variable "subscription_id" {
  description = "Azure Subscription ID donde se crearán los recursos"
  type        = string
}

variable "resource_group_name" {
  description = "Nombre del Resource Group"
  type        = string
  default     = "rg-poc-eventhubs"
}

variable "location" {
  description = "Región de Azure"
  type        = string
  default     = "East US"
}

variable "namespace_name" {
  description = "Nombre del Event Hubs Namespace (debe ser globalmente único)"
  type        = string
  default     = "poc-quarkus-eventhubs-tf"
}

variable "eventhub_name" {
  description = "Nombre del Event Hub (topic)"
  type        = string
  default     = "mi-event-hub"
}

variable "consumer_group_name" {
  description = "Nombre del Consumer Group"
  type        = string
  default     = "quarkus-consumer-group"
}

variable "app_registration_name" {
  description = "Nombre del App Registration (Service Principal)"
  type        = string
  default     = "quarkus-eventhubs-sp-tf"
}
