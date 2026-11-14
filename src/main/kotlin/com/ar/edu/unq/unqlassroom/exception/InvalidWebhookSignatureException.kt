package com.ar.edu.unq.unqlassroom.exception

class InvalidWebhookSignatureException(
    message: String = "Firma HMAC de Webhook inválida o ausente"
) : UnauthorizedException(message, errorCode = "INVALID_WEBHOOK_SIGNATURE")
