import { Link } from 'react-router-dom'
import { Grid, Column, Button, Tag, Tile } from '@carbon/react'
import { ArrowRight, Growth, ChatBot, DocumentAdd, Result } from '@carbon/icons-react'
import PublicHeader from '@/components/layout/PublicHeader'

const PHASES = [
  {
    icon: Growth,
    title: 'Research & Lead Discovery',
    description:
      'Investigate realistic client leads, uncover business signals and build the evidence base for your pitch.',
  },
  {
    icon: DocumentAdd,
    title: 'Outreach & Meeting Prep',
    description:
      'Craft personalised outreach, secure the meeting, then prepare an agenda and discovery questions.',
  },
  {
    icon: ChatBot,
    title: 'AI-Powered Client Meetings',
    description:
      'Practice live, evolving conversations with a watsonx-grounded persona that reacts to your approach in real time.',
  },
  {
    icon: Result,
    title: 'Proposal & Assessment',
    description:
      'Submit a proposal grounded in what you discovered, then receive a detailed competency assessment and coaching feedback.',
  },
]

export default function LandingPage() {
  return (
    <div style={{ background: '#ffffff', minHeight: '100vh' }}>
      <PublicHeader />

      {/* Hero */}
      <section
        style={{
          background: 'linear-gradient(135deg, #ffffff 0%, #edf5ff 100%)',
          padding: '6rem 2rem 5rem',
        }}
      >
        <Grid fullWidth>
          <Column lg={10} md={8} sm={4}>
            <Tag type="blue" style={{ marginBottom: '1.5rem' }}>
              IBM × RMIT Capstone
            </Tag>
            <h1
              style={{
                color: '#161616',
                fontSize: 'clamp(2.5rem, 5vw, 4rem)',
                fontWeight: 600,
                lineHeight: 1.1,
                marginBottom: '1.5rem',
              }}
            >
              Master consulting, the IBM way.
            </h1>
            <p
              style={{
                color: '#525252',
                fontSize: '1.25rem',
                lineHeight: 1.5,
                maxWidth: '640px',
                marginBottom: '2.5rem',
              }}
            >
              An AI-powered training simulation that puts you through a full consulting
              engagement — from lead research to a live client meeting and a winning proposal.
            </p>
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
              <Button as={Link} to="/register" renderIcon={ArrowRight} size="lg">
                Get started free
              </Button>
              <Button as={Link} to="/login" kind="tertiary" size="lg">
                Sign in
              </Button>
            </div>
          </Column>
        </Grid>
      </section>

      {/* Phases */}
      <section style={{ padding: '5rem 2rem' }}>
        <Grid fullWidth>
          <Column lg={16} md={8} sm={4} style={{ marginBottom: '3rem' }}>
            <h2 style={{ color: '#161616', fontSize: '2rem', fontWeight: 600 }}>
              One simulation, four disciplines
            </h2>
            <p style={{ color: '#525252', marginTop: '0.5rem', fontSize: '1rem' }}>
              Every engagement takes you through the full lifecycle a real consultant follows.
            </p>
          </Column>

          {PHASES.map((phase) => {
            const Icon = phase.icon
            return (
              <Column key={phase.title} lg={4} md={4} sm={4} style={{ marginBottom: '1.5rem' }}>
                <Tile style={{ height: '100%' }}>
                  <Icon size={32} style={{ fill: '#0f62fe', marginBottom: '1rem' }} />
                  <h4 style={{ color: '#161616', marginBottom: '0.5rem' }}>{phase.title}</h4>
                  <p style={{ color: '#525252', fontSize: '0.875rem', lineHeight: 1.5 }}>
                    {phase.description}
                  </p>
                </Tile>
              </Column>
            )
          })}
        </Grid>
      </section>

      {/* CTA footer */}
      <section
        style={{
          padding: '4rem 2rem',
          borderTop: '1px solid #e0e0e0',
          background: '#f4f4f4',
          textAlign: 'center',
        }}
      >
        <h3 style={{ color: '#161616', fontSize: '1.5rem', marginBottom: '1.5rem' }}>
          Ready to run your first engagement?
        </h3>
        <Button as={Link} to="/register" renderIcon={ArrowRight} size="lg">
          Create your free account
        </Button>
      </section>
    </div>
  )
}
