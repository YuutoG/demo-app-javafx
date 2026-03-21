package org.ascencio.demoapp;

public enum TipoCuenta {
    ACTIVO("ACTIVO"),
    PASIVO("PASIVO"),
    PATRIMONIO("PATRIMONIO"),
    INGRESO("INGRESO"),
    EGRESO("EGRESO"),
    CUENTA_DE_ORDEN_Y_DE_CONTROL_DEUDORAS("CUENTA DE ORDEN Y DE CONTROL DEUDORAS"),
    CUENTA_DE_ORDEN_Y_DE_CONTROL_ACREEDORAS("CUENTA DE ORDEN Y DE CONTROL ACREEDORAS");

    private final String texto;

    // Constructor del Enum
    TipoCuenta(String texto) {
        this.texto = texto;
    }

    // Para enviar a SQLite y a la pantalla
    public String getTexto() {
        return texto;
    }

    // Para leer desde SQLite o desde el Excel
    public static TipoCuenta desdeTexto(String textoBuscado) {
        for (TipoCuenta tipo : TipoCuenta.values()) {
            if (tipo.texto.equalsIgnoreCase(textoBuscado.trim())) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Clasificación no válida: " + textoBuscado);
    }
}
