# План развития локального AI в Just Cal

Дата сверки реализации и качества: **2026-10-22–23**, приложение **0.17.1 (44)**. Базовая реализация появилась в 0.16.0 (40); качество и критерии завершения остаются частично закрыты. Фактические конфигурации, результаты и оставшиеся проверки находятся в [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md). Разделы исходного плана ниже сохранены как критерии реализации, а не как утверждение о выполненных тестах.

## Решение и границы

Сохранить bundled PP-OCRv5 как основной путь Scan package. Добавить управляемые пользователем модели LiteRT-LM, отдельный AI Playground и небольшой production `FoodRecognitionRouter`. Сначала доказать пользу распознавания изображений, затем подключать её к редактору. Один модуль `app`, существующие Hilt/StateFlow/Room, CameraX/Photo Picker и Navigation 3 остаются основой.

Первый каталог ограничить двумя кандидатами: **Gemma 4 E2B для общего vision/meal** и **PaddleOCR-VL-1.6 для сложного текста**. Их доступность в каталоге не означает готовность к production. Qwen3.5 Vision и SmolVLM2 оставить для сравнения в Playground; второй inference runtime, конвертер моделей, NPU, агентские инструменты, облако и поиск по внешним базам в этот план не входят.

Реализация обновляет конкретные контракты AGENTS.md/DESIGN.md: Playground по-прежнему не пишет в дневник; Scan meal с выбранной допущенной моделью открывает проверяемую подсказку названия. Числовые estimates и package VLM fallback остаются выключенными по результатам quality gates. Визуальные токены и остальные ограничения сохранены.

## Текущая сверка и оставшаяся работа

Подробные raw-ответы, измерения и границы выборки: [quality-audit.md](ai-verification/2026-10-22/quality-audit.md). Успешная сборка, metadata, image smoke и качество задачи — отдельные уровни проверки.

| Шаг | Фактическое состояние | Что ещё требуется |
| --- | --- | --- |
| 1. Runtime spike | LiteRT-LM 0.18.1, оба pinned artifact запускают image inference на API 30; Gemma CPU/CPU, Paddle GPU/GPU | Повторные GPU cancel/retry/exit и контролируемая оценка памяти; другие устройства и 16-KiB-page runtime не проверены |
| 2. Storage / Models | SAF, private atomic commit, pinned length/hash, resumable transfer policy и durable stop/restart реализованы | Полный HTTPS transfer и real 200/206/416/ETag interruption flow; API 34+ UIDT/OS stop, ENOSPC и delete/download race. Маленькие policy/state tests не заменяют эти проверки |
| 3. Playground / Auto / UI | Отдельная сессия, Auto по metadata, native cancellation, скрытый Profile Name с сохранением данных, единая компоновка M3 Expressive | Непрерывные повторные циклы native памяти; широкий accessibility/device sweep вне текущей проверки |
| 4. Package hybrid | PP-OCRv5 остаётся production baseline; bounded crop/transcription и conflict review реализованы | Качество bundled OCR неполное; VLM gate «минимум 2 восстановленных поля, 0 новых неверных чисел» не пройден. Package VLM не включать. Нужны сложные crop/EXIF end-to-end проверки |
| 5. Meal | Допущена только name-only Gemma + строгий validator; сквозной real-model UI тест подтвердил дату, пустой amount, восстановление правок и единственный явный Save | Шире размеченная выборка реальных составных блюд; число nutrients/масса по фото выключены. Импорт новой модели или smoke не создаёт допуск |

В 0.17.1 закрыты дополнительные пробелы: допуск привязан к artifact hash, runtime и версии задачи со ссылкой на evidence; устаревший допуск не применяется автоматически после upgrade. Локальный тайм-аут необязательного package VLM возвращает OCR только после cleanup, отмена пользователя продолжает распространяться. Meal refusal/invalid output сохраняет bounded raw в локальной подробной диагностике и не открывает редактор с пустым «успешным» результатом. Отчёты тестов отдельно считают корректный ответ модели и безопасное отклонение валидатором.

## Исходное состояние до реализации

- `ai/LocalModelStore.kt`: SAF → потоковая private-копия в `noBackupFilesDir/local-ai-models`, UUID, staging/rename, лимит 16 GiB, удаление cache. Сейчас metadata содержит лишь имя; расширение файла не подтверждает vision.
- `ai/LiteRtLocalAiEngine.kt`: LiteRT-LM **0.17.1**, CPU/GPU одновременно для language и vision, один process-wide native permit, новый Conversation на запрос, ожидание завершения native cancellation перед освобождением. Сейчас общий бюджет 4096/1024 токенов и Gemma-ориентированные параметры.
- `ai/LocalAiSession.kt`, `ui/LocalAiLab*`: состояния, raw output, строгий JSON-парсер и реальные метрики; отдельный жизненный цикл без DiaryRepository.
- `ocr/PaddlePackageOcrEngine.kt`: ONNX Runtime/OpenCV, progressive recognition, полный OCR fallback и ограниченное уточнение kcal. `NutritionLabelParser.kt` сохраняет basis, evidence и пропуски; `PackageScanPrefill.kt` передаёт результат в существующий редактор.
- `camera/ImagePreprocessor.kt`: ограниченные upright sRGB изображения; `CameraCapture.kt` сохраняет lifecycle, 4:3 viewport и AF/AE. `PhotoReviewViewModel.kt` пока запускает только package OCR; `JustCalApp.kt` переносит дату в FoodEditor. Диагностика находится в отдельной private Room DB.
- По записи [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md), **2026-10-06** Gemma E2B реально работала с изображением на OnePlus 7T/API 30, CPU и GPU. Но constrained nutrition дала все null; мелкий русский текст не извлечён. Это историческое подтверждение запуска конкретного файла, не проверка новой версии/любого E2B и не доказательство качества питания.

Пути Kotlin ниже относительны `app/src/main/java/com/justcal/app/`. Текущая платформа: minSdk 30, targetSdk 37, arm64-v8a.

## Совместимость и выбор — исходный срез источников

Таблица ниже сохраняет исходный research-срез. Текущие hashes и выполненные проверки находятся в текущей сверке выше и отчёте; внешние карточки альтернатив в этой сверке повторно не проверялись.

Уровни доказательств: **A** — есть запись реального запуска в Just Cal; **B** — автор артефакта публикует image-run на устройстве; **C** — подтверждены формат/API, запуск в Just Cal ещё нужен. Ни B, ни C не допускают автоматического production-включения.

| Модель / точный вариант | Проверено по источникам | Решение |
| --- | --- | --- |
| Gemma 4 E2B, `gemma-4-E2B-it.litertlm` | A для исторического файла 2 588 147 712 B на 0.17.1. [Google](https://developers.google.com/edge/litert-lm/models/gemma-4) подтверждает LiteRT-LM и Apache-2.0. [Gallery 1_0_20](https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_20.json) фиксирует revision `6e5c4f1e395deb959c494953478fa5cec4b8008f`, CPU/GPU и ориентир 8 GB RAM. Совпадение размера с локальным тестом не доказывает совпадение hash. | Первый общий vision-кандидат. Проверять еду и крупный текст; не заменять им OCR мелкой этикетки. Не переносить 32K context Gallery: ограничить бюджет профилем задачи. |
| PaddleOCR-VL-1.6, `PaddleOCR-VL-1.6.litertlm` | B/C: [карточка конверсии](https://huggingface.co/litert-community/PaddleOCR-VL-1.6) описывает ~1.39 GB, Apache-2.0, static 560×560, context 4096, task prompt `OCR:`; template обновлён для 0.18. Есть Pixel 8a CPU image OCR и S26 GPU evidence, но также S26 CPU `degenerate_output` на 0.16.0. | Приоритетный эксперимент для cropped Russian labels. Не подавать универсальный nutrition JSON prompt; сначала транскрипция, затем Kotlin parsing. CPU fallback разрешать только после проверки точной пары artifact/device/backend. |
| Qwen3.5 0.8B / 2B Vision, `Qwen3.5-0.8B-VL_int8.litertlm`, `Qwen3.5-2B-VL_int8.litertlm` | [0.8B](https://huggingface.co/litert-community/Qwen3.5-0.8B), [2B](https://huggingface.co/litert-community/Qwen3.5-2B): Apache-2.0, отдельные text/VL файлы, vision 512×512. 2B VL около 3.15 GB; 0.8B VL около 1.3 GB. Карточки описывают исправления quantization/tokenizer в октябре, ограничение M-RoPE и повторное состояние между Conversation. Android evidence зависит от версии/артефакта; часть измерений только text или iOS. | C для Just Cal. Начать с 0.8B VL только в Playground. **Новый Engine на независимый запрос**, пока reset не доказан. Не распространять результаты text-файла на vision и не выбирать GPU по названию SoC. 2B отложить из-за стоимости памяти/инициализации. |
| SmolVLM2-500M, `SmolVLM2-500M.litertlm` | [Карточка](https://huggingface.co/litert-community/SmolVLM2-500M): Apache-2.0, ~361 MB, 512×512, context 2048, single-image VQA; есть опубликованные Android примеры. | Компактный запасной кандидат для названия/описания еды; качество русского nutrition не установлено. Не использовать общий 4096-token профиль. |
| FastVLM-0.5B | [Карточка](https://huggingface.co/litert-community/FastVLM-0.5B) описывает LiteRT-LM vision и CPU/GPU/NPU, но лицензию **Apple AMLR**, не Apache-2.0. | Вне первого curated-каталога: отдельная проверка условий весов/распространения. Наличие Android demo не решает GPL-совместимость продукта. |
| SmolVLM-256M LiteRT | [Репозиторий](https://huggingface.co/litert-community/SmolVLM-256M-Instruct/tree/main) содержит `.tflite` и tokenizer, а не готовый самодостаточный `.litertlm`. | Не поддерживать этим импортом; не добавлять отдельный runtime ради минимального размера. |

**SDK:** закреплён **LiteRT-LM 0.18.1**. Текущая реализация с этим pin собрана и запускала оба vision-артефакта на физическом API 30. [Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/docs/api/kotlin/getting_started.md) и [Conversation API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt) относятся к этой закреплённой версии. 0.17.1 ниже — историческая runtime-база до реализации, а не текущая версия приложения; `latest.release` не использовать.

Для двух curated-вариантов revision, filename, byte count и SHA-256 уже закреплены в `assets/ai_models.json`. Новые артефакты требуют отдельной проверки. Model card и desktop benchmark не доказывают качество vision на Android. Текущий допуск Gemma относится только к name-only задаче; package VLM gate не пройден.

## Что взять из Google AI Edge Gallery

Использовать как reference раздельные model/download/runtime состояния, versioned allowlist, capability/task mapping, частичную загрузку и ручной import. Проверены [allowlist](https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_20.json), [LiteRtLmModelDownloader](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/LiteRtLmModelDownloader.kt), [DownloadWorker](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/worker/DownloadWorker.kt). Корневой `model_allowlist.json` содержит старые `.task` записи: для нового каталога он не источник совместимости.

Реализовать небольшой собственный каталог, без Gallery task framework, HF login, агента/инструментов и произвольных URL. В просмотренном downloader наличие файла используется как признак скачивания — Just Cal дополнительно требует integrity check. Не копировать URI/path overrides и логи credentials. Gallery Apache-2.0; сохранять notices при любом заимствовании, лицензии SDK и **каждого артефакта весов** учитывать отдельно от GPL-3.0-or-later приложения.

## Минимальная архитектура

`AI Models → LocalModelStore ← ModelDownloadRepository`

`Playground → LocalAiSession → LocalAiEngine / LiteRtLocalAiEngine`

`PhotoReview → FoodRecognitionRouter → PackageOcrEngine или LocalAiEngine → RecognitionDraft → FoodEditor → явный Save`

Playground и production делят storage/runtime, но не историю conversation, UI-сессию или право записи. Новых универсальных plugin/factory слоёв не нужно.

### Metadata и проверка

Новый `ai/ModelDescriptor.kt` разделяет immutable curated descriptor и установленный экземпляр:

| Часть | Поля |
| --- | --- |
| Идентичность | schemaVersion, catalogId, installed UUID, displayName, source (curated/import), upstream URL/revision, filename, expectedBytes, SHA-256, licenseId/licenseUrl/notices |
| Совместимость | runtime=LiteRT-LM, min/tested runtime, input/output modalities, task profile (`LABEL_TRANSCRIPTION`, `GENERAL_VISION`), static/dynamic context и пределы, image/token budgets, допустимые language/vision backend pairs, известные device exclusions |
| Поведение | prompt/parser profile version, structured-output support **по проверке**, engine reuse policy, примерная потребность RAM/cache с источником измерения |
| Local state | relative private path, installed bytes/hash, transfer state, validation status/reason, последняя успешная image-проверка и fingerprint SDK/device/backend |

В `ai/LiteRtModelInspector.kt` использовать [SDK `ModelInfo.from(path).use`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/ModelInfo.kt): тип LLM, vision input, context, min runtime, backend отдельно для TEXT/VISION. JNI остаётся в adapter; результат — app-owned DTO. Metadata не доказывает работоспособность ускорителя или достоверность ответа.

Проверка: ограниченный stream/размер → hash → SDK container/metadata → совместимость профиля → явная загрузка и короткий image probe в Playground. Невалидный container отклонить; text-only/embedding не разрешать для Scan. Неизвестные metadata/профиль дают `Unverified`, а не «Совместимо». Custom import допускает self-contained `.litertlm` через SAF; GGUF, `.task`, raw `.tflite`, ZIP и внешние native plugins не принимаются. Совпавший curated hash получает его профиль; неизвестный файл доступен в Playground после проверки, без автоматического production-routing. Hash custom-файла идентифицирует его, но не удостоверяет автора.

Старые UUID-директории и `metadata.json` читать без переимпорта: добавить nullable/default поля, вычислить hash/проверить при первом использовании, не удалять неизвестный старый файл. Выбор модели для package/meal хранить ключами в существующем DataStore; удаление выбранной модели очищает только соответствующий выбор.

### Storage и загрузка

- Bundled каталог в `app/src/main/assets/ai_models.json`, обновление через релиз приложения. Только общедоступные HTTPS assets с pinned revision/hash; сетевые обращения только после Download/Resume. Ни распознавание, ни запуск приложения не требуют сети. 401/403 — понятная ошибка и SAF import, без аккаунтов.
- Расширить `LocalModelStore`: отдельные `.download-*` и `.import-*`, immutable committed directories, atomic metadata write/fsync/rename. Не применять нынешнюю очистку `.import-*` к resumable downloads. Source URI никогда не становится native path. Исходный пользовательский документ не удалять.
- `ModelDownloadRepository.kt`: один активный transfer, bounded streaming через platform HTTP; resume с Range + If-Range/ETag, проверкой 206/Content-Range. При 200 или смене validator начать заново; 416 обработать проверкой длины/hash либо restart. Проверять redirect HTTPS/доверенный host, не принимать HTML/login вместо модели. При завершении полный SHA-256 и точная длина обязательны.
- До скачивания/импорта оценивать остаток transfer + runtime cache + запас свободного места; при update учитывать старую модель. Повторять проверку в ходе копирования, безопасно обрабатывать ENOSPC. Размер файла не равен пику RAM. Старую рабочую версию не заменять до успешного commit; не удалять другие модели автоматически.
- Для длительных пользовательских загрузок: platform `ModelDownloadJobService` с UIDT на API 34+, небольшой `dataSync` foreground-service fallback на API 30–33, общий transfer executor. Notification progress/cancel, запуск только из допустимого пользовательского контекста; при OS stop сохранять checkpoint, не обещать бессрочную работу. Это сохраняет отсутствие новой WorkManager-зависимости; [Android UIDT](https://developer.android.com/develop/background-work/background-tasks/uidt) предназначен именно для длительных user-initiated transfers.
- Durable состояния: queued/downloading/paused/verifying/installed/failed; runtime-состояния отдельно. Pause сохраняет part, Cancel удаляет его после остановки writer. Process death восстанавливает paused/interrupted, не продолжает inference. Delete сериализуется с runtime/transfer, после unload удаляет model и cache; ошибка удаления видима. Cache namespace: artifact hash + SDK + backend pair + config/device fingerprint.

### Runtime и Auto accelerator

Расширить существующий `LocalAiEngine`, не создавать runtime на каждую модель. Небольшие `ModelTaskProfile` задают prompt, output parser, бюджеты и reuse policy. Paddle → `OCR:` без JSON constraint; Gemma → короткий vision prompt, schema только для проверенного режима; Qwen → fresh Engine per independent run. Thinking/tools выключены. Не навязывать всем моделям Gemma template или 4096 context.

`ai/BackendSelector.kt`: Auto пересекает descriptor, SDK TEXT/VISION metadata и проверенные device ограничения. Начальный безопасный порядок определяется профилем (для проверенного Gemma GPU/GPU → CPU/CPU); смешанные пары добавлять только с evidence. Помнить успешную пару по hash + SDK + OS/device; инвалидировать при изменении. В итоговой реализации CPU/GPU picker отсутствует, согласно прямому требованию задачи. Реальный backend и fallback видны в метриках. NPU не включать без конкретного артефакта/SoC/plugin и отдельной проверки.

Auto допускает **не более одной** повторной попытки на разрешённом backend после recoverable init/delegate failure, полного закрытия старого движка и проверки ресурсов. Не повторять malformed model, invalid output, OOM/low-memory или отмену на другом backend. Не выбирать «самый быстрый» без измерений. В diagnostics записывать requested/actual language+vision backend, попытки и причину fallback; в UI коротко показывать фактически используемый вариант.

Сохранить process-wide permit, off-main JNI и ожидание terminal callback. Production и Playground не держат два VLM; при конфликте показывать Busy. На выходе отменить/дождаться/закрыть, затем удалить owned image/crops. Deadline останавливает публикацию результата и инициирует cancel, но **не разрешает освобождать native ресурсы до завершения**. Зависший JNI/crash нельзя надёжно перехватить в Kotlin: пометить незавершённый запуск при следующем старте, не повторять его автоматически. Отдельный процесс пока не добавлять.

## Распознавание и безопасный результат

**Package:** существующий PP-OCRv5 → текущий детерминированный parser. При полном результате сразу открыть редактор. При missing/ambiguous nutrition, неясном basis либо OCR failure — максимум один дополнительный VLM этап, только с установленной и допущенной моделью. Пользователь может пропустить его и редактировать OCR. Нет модели/ошибка/недостаток памяти — сохранить доступный OCR и предложить retake/manual, без автоматического Download.

Для Paddle передавать один блок nutrition с heading/basis/units, максимум два последовательных ограниченных crop, а не уменьшенную целую упаковку. Использовать OCR bounds, но учитывать внутренний поворот source в `PaddlePackageOcrEngine`: добавить явное преобразование координат к PreparedImage и тесты 90°/180°/EXIF. При отсутствии надёжной области предложить более близкий снимок; не строить многоступенчатый tiling pipeline.

Новый `domain/RecognitionDraft.kt`: поля, source/evidence, basis, missing/conflict, raw-result reference, diagnostic attempt ID; никаких Bitmap/JNI. Отдельный parser adapter для транскрипции переиспользует nutrition rules, но **не фабрикует OcrLine.confidence=1**. VLM-only значения — editable suggestions с видимым происхождением. Несовпадающие OCR/VLM числа не усреднять и не выбирать по self-confidence: спорное поле пустое, обе версии доступны для проверки. Не смешивать per-serving и per-100-g. Пропуски/сомнения остаются null; net mass не равна съеденному количеству автоматически. Существующий OCR prefill массы сохранить; VLM массу показывать отдельно до выбора пользователем.

**Meal:** после допуска Gemma запускать один image+prompt запрос; первый ограниченный продуктовый шаг — один продукт/блюдо на фото, редактируемое название и отдельно помеченные ориентировочные nutrients per 100 g. Количество съеденного пользователь вводит сам; массу/общие калории по фотографии не выдавать за измерение. Неясный состав/несколько блюд → описание и ручной ввод, без декомпозиции рецептов. Все estimates проходят отдельную строгую схему и FoodValidator; арифметика только NutritionCalculator. Если качество не проходит gate, оставить name-only помощь, а числовую оценку выключенной.

Заменять `PackageScanPrefill` постепенно: новый nullable recognition payload в `FoodEditor`, сохранение чтения старых package snapshots. `EditorViewModel` получает проверенный draft и provenance, восстанавливает пользовательские правки раньше prefill, не пишет до Save. Проверить существующий `EntrySource` и backup serializer перед добавлением MEAL_SCAN; старые импорты и portableId должны сохраниться. Playground никогда не вызывает этот production переход и не получает DiaryRepository.

**Диагностика:** расширить `DiagnosticModels.kt`, `OcrDiagnosticSession.kt`, `OcrDiagnosticsRepository.kt` и exporter/UI дополнительными attempt stages, hash/revision, prompt/profile version, SDK, backend, measured timings, raw VLM text и field provenance. Новые nullable поля/аддитивная миграция, старые записи читаются. Correction tracking относится к подтверждённому draft; неизменённое поле не ground truth. Ошибка записи diagnostics не блокирует scan/save. Не писать фото, private paths или credentials в logs/reports; фото только через существующий явный export flow. Partial/cancel/all-null — не success распознавания.

## Settings и Playground

В `SettingsScreen.kt` добавить две строки в существующую tonal navigation group: **AI Models** и **AI Playground**. Это дочерние Settings destinations, не новые нижние tabs. Models: installed/available flat groups, размер, назначение, статус; detail sheet с source/license, Download/Resume/Import, выбором назначения и подтверждением Delete. Скачивание не выбирает модель автоматически. Empty/offline/incompatible/failed имеют текст и понятное следующее действие.

Playground развить из `LocalAiLabScreen/ViewModel`, сохранив отдельную сессию: выбранная модель → изображение → preset/prompt → Run/Cancel → raw result и раскрываемые diagnostics. Управление файлами перенести в Models; никакой кнопки Save to diary. Сохранить старый `LocalAiLab` route identifier при смене отображаемого названия, чтобы не ломать восстановление. Из Scan переход в Models сохраняет request/date и не возобновляет распознавание сам.

Использовать `CalSettingsRow`, `CalGroup`, `CalTopBar`, `CalButton`, существующие progress/motion и семантические цвета; determinate bytes только при известном total. Сохранить bounded reading width, safe insets, floating host и predictive Back. EN/RU resources, масштабирование текста, TalkBack статусы, 48 dp targets; пользовательские ошибки без native paths. Новые визуальные отклонения от DESIGN.md не нужны; перенос Playground с legacy typography — отдельная часть согласуемого UI шага.

**Profile Name:** убрать только поле и пустую Profile-секцию из `SettingsScreen.kt`. Сохранить `displayName` в AppSettings/SettingsDraft/GoalPreferences, ключ DataStore, существующее использование/сериализацию. Другие Settings edits не должны записывать вместо него пустую строку; никакой миграции/очистки данных.

## Порядок реализации и критерии завершения

| Шаг | Конкретные компоненты | Gate |
| --- | --- | --- |
| 1. Runtime spike, без production | `gradle/libs.versions.toml`, `LiteRtLocalAiEngine.kt`, новые `LiteRtModelInspector.kt`, `ModelTaskProfile.kt`; существующие AI tests | Pin 0.18.1, проверить AAR/API30/arm64 packaging и build; metadata и действительный image response для Gemma/Paddle на одном доступном физическом устройстве. Зафиксировать hashes. Не считать text benchmark image-тестом. |
| 2. Хранилище и Models | `LocalModelStore.kt`, `ModelDescriptor.kt`, `ModelDownloadRepository.kt`, download job/service, `AndroidManifest.xml`, asset catalog, `ui/AiModelsScreen.kt` / `AiModelsViewModel.kt`, DataStore keys | Маленькие fixtures: hash mismatch, partial/resume 200/206/416, смена ETag, disk full, cancel/process restart, legacy import, duplicate/download/delete race. Ни повреждённый, ни text-only файл не выбирается для Scan. |
| 3. Playground и Auto | `LocalAiSession.kt`, `LocalAiLab*`, `BackendSelector.kt`, `JustCalApp.kt`, `SettingsScreen.kt`, EN/RU strings | Fake-engine tests для lifecycle/fallback/reuse; ограниченный real image/cancel/retry/exit smoke. Profile Name скрыто, сохранённое значение переживает изменение целей и restart. Краткий visual check новых экранов, без широкого device sweep. |
| 4. Package hybrid | `FoodRecognitionRouter.kt`, `RecognitionDraft.kt`, `PhotoReview*`, parser adapter, crop transform, `Editor*`, diagnostics | Сохранён baseline OCR. На фиксированном небольшом наборе RU этикеток хотя бы 2 previously-missing поля восстановлены верно, ни одного нового неверного автоматически принятого числа; ambiguous basis и конфликты остаются на review. Ноль diary writes до Save. Если нет улучшения — оставить fallback в Playground. |
| 5. Meal, после отдельного gate | meal task/parser, тот же router/editor, provenance и backup compatibility; обновить `LOCAL_AI_LAB.md`, `ARCHITECTURE.md`, нужные контракты `DESIGN.md` | 6–10 различных food/non-food/ambiguous image fixtures: корректное отличие входных изображений, безопасный отказ/пропуски, editable estimate marking, ручной amount, исходная дата, единственная запись после Save; числовые estimates включать только после заранее зафиксированных критериев качества на размеченных блюдах. |

Не расширять шаг 1 на все модели: сначала Gemma + Paddle; Qwen/SmolVLM — только если они решают обнаруженный провал. Расширенный lifecycle smoke должен включать две визуально разные картинки, 3 последовательных запуска, отмену и повтор, выход/повторный вход; actual backend и raw output обязательны. Текущая сверка выше фиксирует выполненные image runs и CPU cancel/retry; полный повторный GPU lifecycle и контролируемые memory cycles пока остаются открытыми.

Для каждого кодового шага сначала узкие `:app:testDebugUnitTest --tests 'com.justcal.app.ai.*'` и затронутые `ocr.*`/`ui.EditorViewModelTest`/новые routing-transfer tests; затем `:app:assembleDebug :app:lintDebug`, IDE diagnostics. Перед production этапом — полный unit suite и проверка backup/диарных регрессий. Использовать Gradle Wrapper. Версию повышать один раз за законченный application-changing этап по AGENTS.md; за этот документ не повышать.

## Главные риски и стоп-условия

- Успешный load/валидный JSON не равны распознаванию. Все-null, повторяющийся текст и одинаковый ответ на разные изображения блокируют production-допуск.
- Native resize теряет мелкий текст; crop должен сохранять basis/units и правильные координаты. Ошибка crop не лечится более длинным prompt.
- GPU может потреблять больше памяти и стартовать медленнее CPU. Никаких универсальных гарантий по RAM/SoC; проверять artifact/backend, не скрывать fallback.
- SDK upgrade может менять template и native lifecycle. Зафиксировать SDK+artifact+profile; working version остаётся доступной до проверки новой.
- Неизвестные custom-модели и JNI могут завершить процесс. В первом этапе только Playground, запрет автоматического повторного запуска и сохранение дневника независимым от AI.
- GPL-код и отдельно загружаемые веса имеют разные лицензии. Сохранять источники/notices, не добавлять restricted-веса в curated-каталог без проверки условий; не считать одно расширение файла разрешением на распространение.

**Результат реализации:** AI Models, AI Playground, pinned LiteRT-LM 0.18.1, Auto по фактической metadata, безопасные transfer/import/runtime boundaries и production router. PP-OCRv5 остаётся основным package engine; VLM fallback не допущен из-за ошибок чисел. Gemma допущена только для редактируемого названия блюда, без числовых estimates. Room/backup schema, update/signing infrastructure сохранены. Полные измерения и ограничения проверки приведены в [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md).
