// Template for the Workflow tool: N review lenses -> 2 verifiers per finding -> report.
// FIX vs earlier runs: a finding whose verifiers never returned (capacity limits, crashes) is
// reported as UNVERIFIED, never silently dropped. "confirmed: 3 of 21" used to mean "18 unverified".
// Copy into the Workflow tool, fill CONTEXT and DIMENSIONS. Inner parallel() takes THUNKS.
export const meta = {
  name: 'adversarial-review',
  description: 'Review a change across lenses; every finding verified by two skeptics or reported as unverified',
  phases: [{ title: 'Review' }, { title: 'Verify' }],
}
const CONTEXT = `...repo, files, house rules...`
const DIMENSIONS = [{ key: 'lens', prompt: `${CONTEXT}\nLENS: ...` }]
const FINDINGS_SCHEMA = { type: 'object', additionalProperties: false, required: ['findings'], properties: { findings: { type: 'array', items: {
  type: 'object', additionalProperties: false, required: ['file', 'line', 'summary', 'failure_scenario', 'severity'],
  properties: { file: { type: 'string' }, line: { type: 'integer' }, summary: { type: 'string' }, failure_scenario: { type: 'string' }, severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] } } } } } }
const VERDICT_SCHEMA = { type: 'object', additionalProperties: false, required: ['refuted', 'reason'], properties: { refuted: { type: 'boolean' }, reason: { type: 'string' } } }

phase('Review')
const results = await pipeline(
  DIMENSIONS,
  d => agent(d.prompt, { label: `review:${d.key}`, phase: 'Review', schema: FINDINGS_SCHEMA }),
  (review, dim) => {
    const found = (review && review.findings) || []
    if (!found.length) return []
    return parallel(found.map(f => () =>
      parallel([
        () => agent(`${CONTEXT}\nSKEPTIC: refute this claimed defect by reading the code. Default refuted=true when uncertain.\n${f.file}:${f.line}\n${f.summary}\n${f.failure_scenario}`, { label: `refute:${dim.key}`, phase: 'Verify', schema: VERDICT_SCHEMA }),
        () => agent(`${CONTEXT}\nREPRO: construct a concrete user sequence that produces the stated wrong outcome; if you cannot, refuted=true.\n${f.file}:${f.line}\n${f.summary}\n${f.failure_scenario}`, { label: `repro:${dim.key}`, phase: 'Verify', schema: VERDICT_SCHEMA }),
      ]).then(votes => {
        const live = votes.filter(Boolean)
        const refutes = live.filter(v => v.refuted).length
        return { ...f, dimension: dim.key, votes: live.length, unverified: live.length === 0, survives: live.length > 0 && refutes < 2 }
      })
    ))
  },
)
const all = results.flat().filter(Boolean)
const confirmed = all.filter(f => f.survives)
const unverified = all.filter(f => f.unverified)
const refuted = all.filter(f => !f.survives && !f.unverified)
log(`${all.length} raw; ${confirmed.length} confirmed, ${refuted.length} refuted, ${unverified.length} UNVERIFIED (verifiers never returned -- read these by hand)`)
return { confirmed, unverified, refutedCount: refuted.length }
