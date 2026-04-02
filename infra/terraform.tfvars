# Copia este archivo como terraform.tfvars y completa los valores
# cp terraform.tfvars.example terraform.tfvars

# REQUERIDO: Tu Subscription ID de Azure
# Puedes obtenerlo con: az account show --query id -o tsv
subscription_id = "8bcaf88c-6bc9-4962-9ee4-d8bfd0cfba14"

# OPCIONALES (tienen valores por defecto en variables.tf)
# resource_group_name   = "rg-poc-eventhubs"
# location              = "East US"
# namespace_name        = "poc-quarkus-eventhubs-tf"   # debe ser globalmente único
# eventhub_name         = "mi-event-hub"
# consumer_group_name   = "quarkus-consumer-group"
# app_registration_name = "quarkus-eventhubs-sp-tf"
