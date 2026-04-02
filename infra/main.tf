# =============================================================================
# Terraform - Azure Event Hubs con autenticación vía Service Principal
# Providers: azurerm ~> 4.0 | azuread ~> 3.0
# =============================================================================

terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
  }
}

# -----------------------------------------------------------------------------
# PROVIDERS
# subscription_id es requerido en azurerm 4.x (breaking change desde 3.x)
# -----------------------------------------------------------------------------
provider "azurerm" {
  subscription_id = var.subscription_id
  features {}
}

provider "azuread" {}

# -----------------------------------------------------------------------------
# DATA SOURCES
# -----------------------------------------------------------------------------
data "azurerm_client_config" "current" {}

# -----------------------------------------------------------------------------
# 1. Resource Group
# -----------------------------------------------------------------------------
resource "azurerm_resource_group" "rg" {
  name     = var.resource_group_name
  location = var.location
}

# -----------------------------------------------------------------------------
# 2. Event Hubs Namespace
#    SKU Standard es necesario para soporte Kafka (puerto 9093)
# -----------------------------------------------------------------------------
resource "azurerm_eventhub_namespace" "eh_ns" {
  name                = var.namespace_name
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  sku                 = "Standard"
  capacity            = 1

  # Kafka está habilitado por defecto en Standard, pero lo dejamos explícito
  # para que quede documentado en el código
  tags = {
    environment = "poc"
    protocol    = "kafka"
  }
}

# -----------------------------------------------------------------------------
# 3. Event Hub (Topic Kafka)
# -----------------------------------------------------------------------------
resource "azurerm_eventhub" "eh" {
  name              = var.eventhub_name
  namespace_id      = azurerm_eventhub_namespace.eh_ns.id
  partition_count   = 2
  message_retention = 1
}

# -----------------------------------------------------------------------------
# 4. Consumer Group
#    El grupo "quarkus-consumer-group" debe existir en Azure para que
#    la app pueda consumir mensajes con ese group.id
# -----------------------------------------------------------------------------
resource "azurerm_eventhub_consumer_group" "cg" {
  name                = var.consumer_group_name
  namespace_name      = azurerm_eventhub_namespace.eh_ns.name
  eventhub_name       = azurerm_eventhub.eh.name
  resource_group_name = azurerm_resource_group.rg.name
}

# -----------------------------------------------------------------------------
# 5. App Registration (Service Principal)
# -----------------------------------------------------------------------------
resource "azuread_application" "app" {
  display_name = var.app_registration_name
}

resource "azuread_service_principal" "sp" {
  # client_id en azuread ~> 3.0 (era application_id en versiones anteriores)
  client_id = azuread_application.app.client_id
}

# -----------------------------------------------------------------------------
# 6. Client Secret
#    application_id recibe el object_id de la aplicación (azuread ~> 3.0)
# -----------------------------------------------------------------------------
resource "azuread_application_password" "sp_secret" {
  application_id = azuread_application.app.id
}

# -----------------------------------------------------------------------------
# 7. Asignación de Rol RBAC
#    "Azure Event Hubs Data Owner" permite producir y consumir mensajes
#    Se asigna a nivel Namespace para cubrir todos los Event Hubs dentro de él
# -----------------------------------------------------------------------------
resource "azurerm_role_assignment" "eh_data_owner" {
  scope                = azurerm_eventhub_namespace.eh_ns.id
  role_definition_name = "Azure Event Hubs Data Owner"
  principal_id         = azuread_service_principal.sp.object_id
}

# =============================================================================
# OUTPUTS — Valores necesarios para application.properties
# =============================================================================

output "kafka_bootstrap_servers" {
  description = "kafka.bootstrap.servers en application.properties"
  value       = "${azurerm_eventhub_namespace.eh_ns.name}.servicebus.windows.net:9093"
}

output "azure_tenant_id" {
  description = "azure.tenant.id en application.properties"
  value       = data.azurerm_client_config.current.tenant_id
}

output "azure_client_id" {
  description = "azure.client.id en application.properties"
  value       = azuread_application.app.client_id
}

output "azure_client_secret" {
  description = "azure.client.secret en application.properties"
  value       = azuread_application_password.sp_secret.value
  sensitive   = true
}

output "azure_eventhub_name" {
  description = "Nombre del Event Hub (topic) — para referencia"
  value       = azurerm_eventhub.eh.name
}

output "consumer_group_name" {
  description = "kafka.group.id en application.properties"
  value       = azurerm_eventhub_consumer_group.cg.name
}

output "application_properties_template" {
  description = "Bloque listo para pegar en application.properties"
  value       = <<-EOT

    # ── Azure Event Hubs con Service Principal ──────────────────────────────
    kafka.bootstrap.servers=${azurerm_eventhub_namespace.eh_ns.name}.servicebus.windows.net:9093

    kafka.security.protocol=SASL_SSL
    kafka.sasl.mechanism=OAUTHBEARER
    kafka.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
    kafka.sasl.login.callback.handler.class=com.nttdata.AzureKafkaCallbackHandler

    azure.client.id=${azuread_application.app.client_id}
    azure.client.secret=<ejecutar: terraform output -raw azure_client_secret>
    azure.tenant.id=${data.azurerm_client_config.current.tenant_id}

    kafka.group.id=${azurerm_eventhub_consumer_group.cg.name}
    kafka.key.serializer=org.apache.kafka.common.serialization.StringSerializer
    kafka.value.serializer=org.apache.kafka.common.serialization.StringSerializer
    kafka.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    kafka.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    # ────────────────────────────────────────────────────────────────────────
  EOT
}
