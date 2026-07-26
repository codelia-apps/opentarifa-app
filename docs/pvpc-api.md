# API de precios PVPC (REE)

Fuente: [apidatos.ree.es](https://www.ree.es/es/apidatos), API pública de Red
Eléctrica de España (REE), sin necesidad de API key.

## Endpoint

```
GET https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real
```

### Parámetros de query

| Parámetro    | Ejemplo               | Descripción                                                        |
|--------------|------------------------|---------------------------------------------------------------------|
| `start_date` | `2026-07-26T00:00`     | Inicio del rango, hora local (sin zona horaria en el parámetro)     |
| `end_date`   | `2026-07-26T23:59`     | Fin del rango                                                       |
| `time_trunc` | `hour`                 | Granularidad de los datos (`hour` para precios PVPC horarios)       |

Headers: `Accept: application/json` (recomendado; la API devuelve JSON por
defecto). No requiere autenticación ni API key.

### Ejemplo de llamada

```
curl "https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real?start_date=2026-07-26T00:00&end_date=2026-07-26T23:59&time_trunc=hour" -H "Accept: application/json"
```

## Formato de la respuesta

```jsonc
{
  "data": {
    "type": "Precios mercado peninsular en tiempo real",
    "id": "mer13",
    "attributes": {
      "title": "Precios mercado peninsular en tiempo real",
      "last-update": "2026-07-25T20:46:25.000+02:00",
      "description": null
    },
    "meta": { "cache-control": { "cache": "MISS" } }
  },
  "included": [
    {
      "type": "PVPC",
      "id": "1001",
      "groupId": null,
      "attributes": {
        "title": "PVPC",
        "color": "#ffcf09",
        "last-update": "2026-07-25T20:46:25.000+02:00",
        "values": [
          {
            "value": 183.18,
            "percentage": 0.5679471677053298,
            "datetime": "2026-07-26T00:00:00.000+02:00"
          },
          { "...": "24 valores en total, uno por hora" }
        ]
      }
    },
    {
      "type": "Precio mercado spot",
      "id": "600",
      "attributes": {
        "title": "Precio mercado spot",
        "values": [ "... 96 valores (cuartos de hora)" ]
      }
    }
  ]
}
```

### Notas importantes

- `included` es un array con **varias series**; para el precio PVPC hay que
  quedarse con el elemento donde `type == "PVPC"` (`id == "1001"`). El otro
  elemento (`"Precio mercado spot"`, `id == "600"`) tiene 96 valores
  (resolución de 15 min) y **no** es el precio PVPC — hay que ignorarlo.
- Cada entrada de `values` para la serie PVPC representa **una hora** del día
  (24 valores para un día completo).
- **`value` viene en €/MWh**, no en €/kWh. Para mostrar el precio habitual al
  usuario (€/kWh) hay que dividir entre 1000.
  Ejemplo: `value: 183.18` €/MWh → `0.18318` €/kWh.
- `datetime` viene en ISO-8601 con offset de zona horaria local de España
  (`+01:00` en invierno, `+02:00` en verano), no en UTC.
- `percentage` no es relevante para mostrar el precio (es la posición del
  valor entre el máximo y el mínimo del día); se puede ignorar.
- La API no requiere API key ni cabeceras de autenticación.
- CORS abierto (`Access-Control-Allow-Origin: *`), pensado también para
  consumo desde navegador.

## Uso en la app

Para obtener los precios de "hoy" en la pantalla principal:

1. Calcular `start_date` = hoy a las `00:00` y `end_date` = hoy a las `23:59`
   en hora local (Europe/Madrid).
2. Llamar al endpoint con `time_trunc=hour`.
3. Quedarse con `included[].attributes.values` del elemento con
   `id == "1001"` (serie `PVPC`).
4. Convertir cada `value` de €/MWh a €/kWh dividiendo entre 1000.
5. Mostrar `datetime` (solo la hora) junto al precio en €/kWh.
