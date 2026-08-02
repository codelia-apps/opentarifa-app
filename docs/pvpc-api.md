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

## Cobertura geográfica (geo_id / geo_trunc / geo_limit)

> Investigado el 2026-08-02 contra el endpoint en producción, antes de
> implementar un selector de región. **Conclusión: este endpoint es
> peninsular únicamente.** No se puede obtener PVPC de Canarias, Baleares,
> Ceuta o Melilla desde `precios-mercados-tiempo-real`, con ningún parámetro.

### Qué se probó

Llamadas al mismo rango de fechas (`2026-08-02`, `time_trunc=hour`) variando
`geo_ids` (8741 Península, 8742 Canarias, 8743 Baleares, 8744 Ceuta, 8745
Melilla), `geo_trunc` y `geo_limit`, incluyendo una llamada base sin ningún
parámetro `geo_*`.

### Resultado por parámetro

- **`geo_limit`** es el único parámetro que realmente tiene efecto, y solo
  acepta el valor `peninsular`:
  - `geo_limit=peninsular` (con o sin `geo_trunc`) → `200`, devuelve los
    precios peninsulares de siempre.
  - `geo_limit=canarias`, `baleares`, `ceuta`, `melilla` (con
    `geo_trunc=electric_system`) → **`HTTP 400`** para las 4, con el mismo
    cuerpo genérico:
    ```json
    {"errors":[{"status":"400","title":"Error Interno","detail":"Los datos solicitados no están disponibles en este momento. Inténtelo de nuevo más tarde."}]}
    ```
    Un `geo_limit` inventado (`nonexistentregion`) da exactamente el mismo
    error, así que esta respuesta no permite distinguir "región no
    soportada" de "parámetro inválido".
- **`geo_ids` se ignora por completo.** Enviar `geo_ids=8742` (Canarias)
  junto con `geo_limit=peninsular` sigue devolviendo los precios de
  Península sin error ni aviso — ni siquiera valida que el id "encaje" con
  el `geo_limit`. Da igual el id que se mande (o varios): no filtra nada en
  este endpoint.
- **`geo_trunc`** solo hace falta si se manda `geo_limit`: `geo_trunc` solo
  (sin `geo_limit`) → `400`. `geo_limit=peninsular` solo (sin `geo_trunc`)
  → `200`, funciona igual que mandando ambos.
- Efecto colateral menor: al añadir `geo_trunc`/`geo_limit` explícitos, la
  serie `"Precio mercado spot"` (`id: "600"`) desaparece de `included` y
  solo queda la serie `PVPC` (`id: "1001"`). No afecta a la app, que ya
  filtra por `id == "1001"`.
- Se probó también un nombre de endpoint alternativo,
  `precios-territorios-no-peninsulares`, por si REE expusiera un widget
  separado para esos territorios: no existe (error interno HTML, ni
  siquiera devuelve JSON).

### Qué significa esto para un futuro selector de región

No es viable implementar un selector Península/Canarias/Baleares/Ceuta/
Melilla usando `precios-mercados-tiempo-real` — el endpoint no separa esos
territorios bajo ningún parámetro conocido. Alternativas a explorar si se
retoma esto:

1. Buscar si REE expone esas tarifas en otro widget de apidatos.ree.es
   (no localizado en esta investigación).
2. Consultar directamente **ESIOS** (`api.esios.ree.es`), que sí desglosa
   varios indicadores por `geo_id`, pero requiere token de autenticación
   (a diferencia de apidatos.ree.es, que es público sin API key) — habría
   que verificar primero si el indicador PVPC concreto está desglosado por
   `geo_id` ahí antes de invertir en integrarlo.

### Decisión: selector de región aparcado

**El selector de región queda aparcado por ahora.** No se va a implementar
mientras la única fuente de datos de la app sea apidatos.ree.es:

- apidatos.ree.es **solo soporta Península** para este endpoint (ver
  investigación arriba) — no hay parámetro que lo evite.
- La alternativa, **ESIOS**, se descarta por el momento porque requiere
  gestionar un token de autenticación (registro de usuario + credencial),
  lo que no encaja con el enfoque actual del proyecto: sin registro para el
  usuario y sin secretos en un repositorio público (la app es open source,
  ver README/LICENSE). Añadir ESIOS implicaría o bien pedir al usuario que
  aporte su propio token, o bien guardar uno del proyecto en el repo o en
  un backend propio — ambas opciones son un cambio de alcance mayor que
  este paso.

Si en el futuro cambia el enfoque del proyecto (p. ej. se añade un backend
propio que pueda guardar el token de ESIOS de forma segura), retomar desde
el punto 2 de la lista anterior.

## Uso en la app

Para obtener los precios de "hoy" en la pantalla principal:

1. Calcular `start_date` = hoy a las `00:00` y `end_date` = hoy a las `23:59`
   en hora local (Europe/Madrid).
2. Llamar al endpoint con `time_trunc=hour`.
3. Quedarse con `included[].attributes.values` del elemento con
   `id == "1001"` (serie `PVPC`).
4. Convertir cada `value` de €/MWh a €/kWh dividiendo entre 1000.
5. Mostrar `datetime` (solo la hora) junto al precio en €/kWh.
