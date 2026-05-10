# youtube-transcript-and-summarize

Fetch YouTube transcripts with the `youtube-transcript` tool and summarize their central ideas.

## Flow
1. check the url look like "https://www.youtube.com/watch?v=<KEY>", if only <KEY> then normalize with https://www.youtube.com/watch?v=<KEY>
2. Run `youtube-transcript`
3. Inline transcript text into the tool JSON result
4. Ask the LLM to produce:
   - an overall theme summary
   - main ideas
   - per-video short summaries
   - notes about failures, truncation, or missing transcripts

## Notes

- This skill depends on the external `youtube-transcript` tool.
- Install the tool's Python dependencies from `workspace/tools/youtube-transcript/requirements.txt`.
- Transcript text may be truncated according to `maxCharsPerTranscript`.
- If `summaryOutput` is omitted, the summary is saved next to the transcript output directory using `<videoId>.summary.md`.
