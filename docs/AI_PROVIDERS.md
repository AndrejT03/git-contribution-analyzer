# AI provider and model contract

This document defines the browser/backend contract for request-scoped AI credentials and provider routing. 
## Runtime contract

The form has two required AI controls:

- `aiModel`: the first AI control in DOM/tab order, whose value identifies both the provider and model;
- `aiKey`: a password input containing a credential issued by the selected provider, bounded to 1,024 characters.

Changing between models owned by the same provider preserves the entered key. Changing to a different provider clears it, returns the input to password mode, and announces the reset through the live helper text. This prevents a credential intended for one provider from later being sent to another provider accidentally.

Jakarta Validation rejects blank keys, line breaks, oversized values, malformed namespaces, and provider/model combinations outside the server-owned allowlist. `ReportServiceImpl` parses the selection, passes the selected model and key to `AiAnalysisService`, and labels the completed report with the real provider/model. The key exists only in the submitted request and worker call chain; it is not copied to `AnalysisJob`, `AnalysisReport`, status JSON, email, PDF, logs, or configuration.

The optional local `.env` file configures operational and SMTP settings; AI clients use the key and model submitted through the form. The reusable HTTP transport is credential-free; it adds the submitted key as a Bearer header only for the current provider request.

## Stable value format

Every model option uses this value format:

```text
<provider>::<model>
```

Examples:

```text
google::gemini-3.8-flash
openai::gpt-5.6-terra
anthropic::claude-sonnet-5
openrouter::~openai/gpt-latest
huggingface::openai/gpt-oss-120b:fastest
```

The parser:

1. splits on the **first** `::` only;
2. requires a nonblank provider and model;
3. matches the provider against a fixed server-side registry;
4. validates the complete model remainder against that provider's fixed allowlist;
5. preserves punctuation and case in the model remainder, including `/`, `~`, `-`, `.`, and `:`;
6. never infers a provider from the API-key prefix.

The provider namespace, not the appearance of the key or model slug, determines the endpoint and adapter. This matters because several providers host the same model family and aggregators expose third-party model names.

## Current browser catalog

The supplied dropdown contains **66 text/chat model options across 11 providers**, including **11 OpenAI** and **11 Anthropic Claude** options. Each option is a distinct `provider::model` combination; a model offered through multiple providers counts once in each provider group. The disabled “Choose a provider and model” placeholder is excluded. The table below preserves the provider groups, option values, visible labels, and ordering from the supplied HTML.

| Provider group | Model options |
|---|---:|
| Google Gemini | 5 |
| OpenAI | 11 |
| Anthropic Claude | 11 |
| OpenRouter | 14 |
| xAI | 1 |
| DeepSeek | 2 |
| Mistral AI | 3 |
| Groq | 5 |
| Together AI | 7 |
| Hugging Face | 5 |
| Cerebras | 2 |
| **Total** | **66** |

The catalog offers direct providers plus broad aggregators without allowing user-controlled endpoints. The OpenRouter entries retain the earlier snapshot of its top-ten weekly text models by processed tokens through 2026-09-02; that ranking measures adoption on OpenRouter, not quality or whole-market share. Labels such as “Recommended”, “Most advanced”, “Fast”, “Free”, “Preview”, or “Popular” reproduce UI guidance and do not guarantee pricing, lifecycle, or quality.

OpenAI includes GPT-6/GPT-5.6 options and earlier GPT-5.5, GPT-5.4, GPT-5.2, GPT-5.1, GPT-4.1, and GPT-4o families. Anthropic includes eleven model IDs. This documentation update follows the supplied HTML; the earlier provider-documentation checks remain dated 2026-09-07 for OpenAI and Anthropic and 2026-09-03 for the other groups. Automated tests use mocks and do not certify live access for any particular account.

| Provider group | Option value | Visible model label |
|---|---|---|
| Google Gemini | `google::gemini-3.8-flash` | Gemini 3.8 Flash — Recommended |
| Google Gemini | `google::gemini-3.7-flash` | Gemini 3.7 Flash — Popular |
| Google Gemini | `google::gemini-3.6-flash` | Gemini 3.6 Flash — Stable |
| Google Gemini | `google::gemini-3.5-flash-lite` | Gemini 3.5 Flash Lite — Low cost |
| Google Gemini | `google::gemini-3.1-pro-preview` | Gemini 3.1 Pro — Preview |
| OpenAI | `openai::gpt-6-astra` | GPT-6 Astra — Most advanced Complex reasoning |
| OpenAI | `openai::gpt-5.6-sol` | GPT-5.6 Sol — Flagship |
| OpenAI | `openai::gpt-5.6-terra` | GPT-5.6 Terra — Recommended |
| OpenAI | `openai::gpt-5.6-luna` | GPT-5.6 Luna — Fast |
| OpenAI | `openai::gpt-5.5` | GPT-5.5 |
| OpenAI | `openai::gpt-5.4` | GPT-5.4 |
| OpenAI | `openai::gpt-5.4-mini` | GPT-5.4 Mini |
| OpenAI | `openai::gpt-5.2` | GPT-5.2 |
| OpenAI | `openai::gpt-5.1` | GPT-5.1 |
| OpenAI | `openai::gpt-4.1` | GPT-4.1 — Non-reasoning |
| OpenAI | `openai::gpt-4o` | GPT-4o |
| Anthropic Claude | `anthropic::claude-fable-5-1` | Claude Fable 5.1 — Most advanced Deep reasoning |
| Anthropic Claude | `anthropic::claude-fable-5` | Claude Fable 5 |
| Anthropic Claude | `anthropic::claude-opus-5` | Claude Opus 5 — Complex work |
| Anthropic Claude | `anthropic::claude-sonnet-5` | Claude Sonnet 5 — Recommended |
| Anthropic Claude | `anthropic::claude-opus-4-8` | Claude Opus 4.8 |
| Anthropic Claude | `anthropic::claude-opus-4-7` | Claude Opus 4.7 |
| Anthropic Claude | `anthropic::claude-opus-4-6` | Claude Opus 4.6 |
| Anthropic Claude | `anthropic::claude-sonnet-4-6` | Claude Sonnet 4.6 |
| Anthropic Claude | `anthropic::claude-opus-4-5-20251101` | Claude Opus 4.5 |
| Anthropic Claude | `anthropic::claude-sonnet-4-5-20250929` | Claude Sonnet 4.5 |
| Anthropic Claude | `anthropic::claude-haiku-4-5-20251001` | Claude Haiku 4.5 — Fast |
| OpenRouter | `openrouter::openrouter/auto` | Auto Router — Recommended |
| OpenRouter | `openrouter::~openai/gpt-latest` | Latest OpenAI model |
| OpenRouter | `openrouter::~anthropic/claude-sonnet-latest` | Latest Claude Sonnet |
| OpenRouter | `openrouter::~google/gemini-flash-latest` | Latest Gemini Flash |
| OpenRouter | `openrouter::openai/gpt-5.6-luna` | GPT-5.6 Luna — Popular Sep 2026 |
| OpenRouter | `openrouter::z-ai/glm-5.3-flash` | GLM 5.3 Flash — Popular Sep 2026 |
| OpenRouter | `openrouter::deepseek/deepseek-v4-flash-0731` | DeepSeek V4 Flash 0731 — Popular |
| OpenRouter | `openrouter::tencent/hy4-preview` | Tencent Hy4 — Preview |
| OpenRouter | `openrouter::xiaomi/mimo-v2.5` | Xiaomi MiMo V2.5 |
| OpenRouter | `openrouter::tencent/hy3` | Tencent Hy3 |
| OpenRouter | `openrouter::deepseek/deepseek-v4-flash` | DeepSeek V4 Flash 0423 |
| OpenRouter | `openrouter::minimax/minimax-m3:free` | MiniMax M3 — Free |
| OpenRouter | `openrouter::nvidia/nemotron-3-ultra-550b-a55b:free` | Nemotron 3 Ultra — Free |
| OpenRouter | `openrouter::google/gemini-3.7-flash` | Gemini 3.7 Flash |
| xAI | `xai::grok-4.6` | Grok 4.6 |
| DeepSeek | `deepseek::deepseek-v4-pro` | DeepSeek V4 Pro |
| DeepSeek | `deepseek::deepseek-v4-flash` | DeepSeek V4 Flash |
| Mistral AI | `mistral::mistral-medium-3-5` | Mistral Medium 3.5 |
| Mistral AI | `mistral::mistral-small-2603` | Mistral Small 4 |
| Mistral AI | `mistral::mistral-large-2512` | Mistral Large 3 |
| Groq | `groq::openai/gpt-oss-120b` | GPT-OSS 120B on Groq |
| Groq | `groq::openai/gpt-oss-20b` | GPT-OSS 20B on Groq |
| Groq | `groq::llama-3.3-70b-versatile` | Llama 3.3 70B — Production |
| Groq | `groq::llama-3.1-8b-instant` | Llama 3.1 8B — Fast |
| Groq | `groq::qwen/qwen3.8-27b` | Qwen 3.8 27B — Preview |
| Together AI | `together::openai/gpt-oss-120b` | GPT-OSS 120B |
| Together AI | `together::Qwen/Qwen3.8-Flash` | Qwen 3.8 Flash |
| Together AI | `together::Qwen/Qwen3.6-Plus` | Qwen 3.6 Plus |
| Together AI | `together::moonshotai/Kimi-K3` | Kimi K3 |
| Together AI | `together::zai-org/GLM-5.3-Flash` | GLM 5.3 Flash |
| Together AI | `together::MiniMaxAI/MiniMax-M3` | MiniMax M3 |
| Together AI | `together::deepseek-ai/DeepSeek-V4-Pro-0813` | DeepSeek V4 Pro 0813 |
| Hugging Face | `huggingface::openai/gpt-oss-120b:fastest` | GPT-OSS 120B — Fastest provider |
| Hugging Face | `huggingface::zai-org/GLM-5.3` | GLM 5.3 |
| Hugging Face | `huggingface::zai-org/GLM-5.3-Flash` | GLM 5.3 Flash |
| Hugging Face | `huggingface::Qwen/Qwen3-Coder-480B-A35B-Instruct` | Qwen 3 Coder 480B |
| Hugging Face | `huggingface::deepseek-ai/DeepSeek-R1` | DeepSeek R1 |
| Cerebras | `cerebras::gpt-oss-120b` | GPT-OSS 120B on Cerebras |
| Cerebras | `cerebras::gemma-4-31b` | Gemma 4 31B on Cerebras |
The supplied HTML also provides example key placeholders: `AQ…` for Google Gemini, `sk-…` for OpenAI and DeepSeek, `sk-ant-…` for Anthropic Claude, `sk-or-v1-…` for OpenRouter, `xai-…` for xAI, `Mistral API key` for Mistral AI, `gsk_…` for Groq, `Together API key` for Together AI, `hf_…` for Hugging Face, and `csk-…` for Cerebras. They are hints only. They must not be used as authentication validation or routing logic.

## Implemented endpoint registry

| Namespace | Fixed Chat Completions endpoint | Backend note |
|---|---|---|
| `google` | `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` | Google's documented OpenAI-compatible surface; failures still name Google Gemini. |
| `openai` | `https://api.openai.com/v1/chat/completions` | Uses the selected GPT model with the shared minimal message payload. |
| `anthropic` | `https://api.anthropic.com/v1/chat/completions` | Uses Anthropic's documented OpenAI SDK compatibility layer; no unsupported `response_format` field is sent. |
| `openrouter` | `https://openrouter.ai/api/v1/chat/completions` | Preserves broad catalog slugs, auto-routing, and `~...-latest` aliases exactly. |
| `xai` | `https://api.x.ai/v1/chat/completions` | Uses the documented OpenAI-compatible request shape at the xAI host. |
| `deepseek` | `https://api.deepseek.com/chat/completions` | Uses DeepSeek's documented OpenAI-compatible base and its own allowlist. |
| `mistral` | `https://api.mistral.ai/v1/chat/completions` | Uses Mistral Chat Completions; retired options must be removed from both UI and registry. |
| `groq` | `https://api.groq.com/openai/v1/chat/completions` | Uses Groq's compatibility API and hosted-model limits. |
| `together` | `https://api.together.ai/v1/chat/completions` | Uses Together's compatibility API and serverless model identifiers. |
| `huggingface` | `https://router.huggingface.co/v1/chat/completions` | Preserves router suffixes such as `:fastest` as part of the model identifier. |
| `cerebras` | `https://api.cerebras.ai/v1/chat/completions` | Uses Cerebras' compatibility endpoint and public model catalog. |

Every call sends only `model`, one user `message`, and `stream=false`, with the submitted key in `Authorization: Bearer ...`. This deliberately small common denominator works across the eleven compatibility surfaces. Responses may return text directly or as typed text parts. HTTP/transport failures, refusals, empty output, malformed JSON, and domain-invalid analysis are translated to the provider-aware `AiFailureReason` taxonomy before local fallback.

An OpenAI-compatible wire shape does not make providers interchangeable operationally. Authentication validity, token limits, error bodies, model retirement, safety behavior, pricing, and quotas remain provider-specific. A model can therefore be allowlisted yet temporarily unavailable; that condition produces a controlled fallback rather than exposing the raw provider response.

## What “other providers” means

There is no safe, accurate way to support literally every provider with only an API key and model field.

- OpenRouter gives one credential access to a large, changing multi-provider catalog and is the main broad-coverage option in this UI.
- Hugging Face Inference Providers supplies another routed catalog for supported models.
- Fireworks and Perplexity expose OpenAI-compatible APIs and model discovery, so they are reasonable future fixed-registry additions after adapter and test work.
- Azure OpenAI requires resource/deployment/API-version metadata beyond a key and model.
- Amazon Bedrock and Google Vertex AI normally require cloud-region/project and IAM-style authentication rather than a single provider key.
- Ollama, LM Studio, and arbitrary compatible servers require a base URL. Allowing a user-supplied URL would create a server-side request forgery boundary and cannot be added as an unvalidated “Other” option.

Therefore the app implements every provider shown in the dropdown through a finite audited registry instead of a misleading generic provider. A future custom-endpoint mode needs an explicit base-URL control, HTTPS and host policy, DNS/IP revalidation, redirect restrictions, private/link-local/loopback address blocking, port rules, timeouts, response-size limits, and administrator-controlled allowlisting.

## Credential and privacy guarantees

The UI statement “used only for this analysis and not stored” is enforced as follows:

- Accept the key only in the HTTPS POST body; never place it in a URL, query string, redirect, cookie, browser storage, analytics event, or error message.
- Require HTTPS at the production ingress; loopback-only local development is the sole plain-HTTP exception.
- Never log the request body, authorization header, bound credential, provider response body, or an exception object that could contain them.
- Never copy the key into `AnalysisJob`, `AnalysisReport`, public status DTOs, email/PDF models, persistence entities, cache keys, metrics labels, or tracing attributes.
- Do not repopulate the password input after validation errors. Return only a generic “enter the key again” message.
- Keep the credential reference only for the queued/running analysis boundary, release it on success, fallback, cancellation, timeout, and exception paths, and document that immutable Java `String` instances cannot be reliably zeroed in memory.
- Build provider clients per request or use credential-free reusable transports; never mutate a singleton client with a user's key.
- Select the destination from the server-owned provider registry. The user-controlled model value must never become a host or base URL.
- Redact known credential formats defensively, but do not depend on prefixes because valid formats can change.
- Apply a 20-second connect timeout, configurable 30–600 second read timeout, two-MiB response limit, no redirects, and provider-aware rate/quota handling.
- Preserve the current rule that repository facts are untrusted prompt data and validate every structured analysis before rendering it.
- Make fallback notices provider-neutral and privacy-safe: expose a controlled category, not raw responses, keys, URLs, filesystem paths, or internal exception detail.

The asynchronous job design needs special care. It may be necessary to retain a credential in process memory until a queued worker starts, but that secret must live in a private, short-lived execution object rather than the public/stored job model, and all references must be dropped as soon as the provider call finishes.

## Implemented backend seams

- parse `aiModel` into a validated `AiProviderSelection(provider, model)` value;
- use an `AiAnalysisService`/`AiProviderClient` boundary with the fixed `AiProvider` registry;
- use `OpenAiCompatibleProviderClient` as a credential-free transport that selects only registry-owned endpoints;
- retain the provider-neutral `ContributionAnalysisDto` validation and deterministic local fallback;
- publish `AnalysisSource`/engine metadata without exposing credentials;
- use provider-neutral `AiAnalysisServiceImpl`, `AiPromptBuilder`, `AiProviderException`, `AiFailureReason`, and `AiFailureCategory` names across production and tests.

The stable external contract is the pair `aiKey` plus namespaced `aiModel`; endpoint ownership and routing remain server-side.

## Official references

### Google Gemini

- [Gemini models](https://ai.google.dev/gemini-api/docs/models)
- [OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai)
- [Gemini deprecations](https://ai.google.dev/gemini-api/docs/deprecations)

### OpenAI

- [Latest model guidance](https://developers.openai.com/api/docs/guides/latest-model)
- [Model catalog](https://developers.openai.com/api/docs/models/all)
- [Model deprecations](https://developers.openai.com/api/docs/deprecations)
- [GPT-6 Astra](https://developers.openai.com/api/docs/models/gpt-6-astra)
- [GPT-5.5](https://developers.openai.com/api/docs/models/gpt-5.5)
- [GPT-5.4](https://developers.openai.com/api/docs/models/gpt-5.4) and [Mini](https://developers.openai.com/api/docs/models/gpt-5.4-mini)
- [GPT-5.2](https://developers.openai.com/api/docs/models/gpt-5.2) and [GPT-5.1](https://developers.openai.com/api/docs/models/gpt-5.1)
- [GPT-4.1](https://developers.openai.com/api/docs/models/gpt-4.1)
- [GPT-4o](https://developers.openai.com/api/docs/models/gpt-4o)
- [Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create)
- [Responses API reference](https://developers.openai.com/api/reference/cli/resources/responses/methods/create)

### Anthropic Claude

- [Models overview](https://platform.claude.com/docs/en/models/overview)
- [Model IDs and versions](https://platform.claude.com/docs/en/about-claude/models/model-ids-and-versions)
- [Messages API](https://platform.claude.com/docs/en/api/messages/create)
- [OpenAI SDK compatibility](https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk)
- [Model deprecations](https://platform.claude.com/docs/en/about-claude/model-deprecations)

### OpenRouter

- [Quickstart](https://openrouter.ai/docs/quickstart)
- [Models API](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties)
- [Usage rankings](https://openrouter.ai/rankings)
- [Latest model resolution](https://openrouter.ai/docs/guides/routing/routers/latest-resolution)
- [Auto Router](https://openrouter.ai/docs/guides/routing/routers/auto-router)

### xAI

- [Grok 4.6](https://docs.x.ai/developers/grok-4-6)
- [Models and pricing](https://docs.x.ai/developers/models)

### DeepSeek

- [API documentation](https://api-docs.deepseek.com/)
- [Models and pricing](https://api-docs.deepseek.com/quick_start/pricing)

### Mistral AI

- [Model overview](https://docs.mistral.ai/models)
- [Models API](https://docs.mistral.ai/api/endpoint/models)
- [Model lifecycle](https://docs.mistral.ai/inference/model-lifecycle)

### Groq

- [Supported models](https://console.groq.com/docs/models)
- [OpenAI compatibility](https://console.groq.com/docs/openai)

### Together AI

- [OpenAI compatibility](https://docs.together.ai/docs/inference/openai-compatibility)
- [Serverless models](https://docs.together.ai/docs/serverless/models)

### Hugging Face

- [Inference Providers](https://huggingface.co/docs/inference-providers/index)
- [Inference Providers API](https://huggingface.co/docs/inference-providers/hub-api)

### Cerebras

- [Models overview](https://inference-docs.cerebras.ai/models/overview)
- [OpenAI GPT OSS](https://inference-docs.cerebras.ai/models/openai-oss)

### Additional compatible providers considered for the future

- [Fireworks OpenAI compatibility](https://docs.fireworks.ai/tools-sdks/openai-compatibility)
- [Fireworks model discovery](https://docs.fireworks.ai/api-reference/list-models)
- [Perplexity Agent API compatibility](https://docs.perplexity.ai/docs/agent-api/quickstart)
