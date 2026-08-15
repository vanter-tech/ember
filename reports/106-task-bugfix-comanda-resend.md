# Report 106 — bugfix-comanda-resend

## 1. Identification
- **Report Number:** 106
- **Task ID:** bugfix-comanda-resend
- **Predecessor Task:** docs-architecture-word (report 105)

## 2. Objective
Stop the customer "Enviar a cocina" button from staying enabled after a successful send, which let a customer resubmit the same already-confirmed items.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/event/ItemSent.java`
- `backend/src/main/java/com/vanter/ember/session/listener/SessionWebSocketListener.java`
- `frontend/src/store/websocket.ts`

## 4. What Changed?
`ItemSent` gained a `type` discriminator (`"ITEMS_CONFIRMED"`, following the existing `ItemAdded`/`DeleteItem` canonical-constructor convention) plus `sessionId`/`sessionItems`, replacing the raw `Session` payload it used to carry. `SessionWebSocketListener` got a new `onItemSent` listener broadcasting it to `/topic/session/{sessionId}`, matching every other session event. `websocket.ts`'s STOMP handler got a new `ITEMS_CONFIRMED` branch that calls `updateSession({items: eventData.sessionItems})`, the same pattern already used for `ITEM_ADDED`.

## 5. Why It Changed?
`SessionService.confirmDraftsForUser` already flipped items from `DRAFT` to `PENDING` server-side and published `ItemSent`, but `SessionWebSocketListener` had no listener for that event at all — it was published into the void. The frontend's Zustand session store (which `ComandaView`'s `tengoBorradores` gate reads) never received the status change, so items stayed `DRAFT` locally forever and the Send button (`disabled={!tengoBorradores}`) never disabled, letting the customer attempt to resend. Broadcasting a raw `Session` was avoided because it carries `joinCode`, which would leak an invite code to every participant on the session topic.
