# Анимации из Blockbench

Сюда кладутся модели `.bbmodel`, сделанные на [модели с суставами](../README.md)
(на GitHub: Add file → Upload files). Визуальный самотест переводит все анимации из них для игры и
проигрывает каждую в Minecraft с Kinetrix: Actions → Visual self-test → Run workflow, в поле `only`
написать `^99`. Кадры из игры (`99_bb_<имя>_*.png`) и картинки-сравнения с Blockbench
(`bbpreview_<имя>.png`) появляются в ветке `selftest` (или `selftest-<ветка>`, если запуск не с `main`).

Что держит игрок в тесте, берётся из модели: лук, если анимирован или показан `right_item_bow`,
второй меч, если анимирован или показан `left_item_sword`.
