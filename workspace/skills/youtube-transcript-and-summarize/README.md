# youtube-transcript-and-summarize

Fetch YouTube transcripts with the `youtube-transcript` tool and summarize their central ideas.

## Flow

1. Run `youtube-transcript`
2. Inline transcript text into the tool JSON result
3. Ask the LLM to produce:
   - an overall theme summary
   - main ideas
   - per-video short summaries
   - notes about failures, truncation, or missing transcripts

## Example

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill run \
  --name youtube-transcript-and-summarize \
  --input-json "{\"input\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}" \
  --provider groq \
  --model llama-3.1-8b-instant \
  --confirm \
  --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

Save the summary to a specific file:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill run \
  --name youtube-transcript-and-summarize \
  --input-json "{\"input\":\"https://www.youtube.com/watch?v=Dv1lpwrkfVc\",\"summaryOutput\":\"D:/tmp/youtube-summary.md\"}" \
  --provider groq \
  --model llama-3.1-8b-instant \
  --confirm \
  --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

## Notes

- This skill depends on the external `youtube-transcript` tool.
- Install the tool's Python dependencies from `workspace/tools/youtube-transcript/requirements.txt`.
- Transcript text may be truncated according to `maxCharsPerTranscript`.
- If `summaryOutput` is omitted, the summary is saved next to the transcript output directory using `<videoId>.summary.md`.
