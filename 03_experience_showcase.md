# Enterprise Experience Showcase

*Note: drafted from my background at UKG — please review and adjust any
specifics before submitting, since this section is evaluated partly on
how concretely and accurately I can speak to it if asked in a follow-up
interview.*

---

## A financial or ERP system I built, and its business impact

At UKG, I designed and built the Compensation Statements product from the
ground up — a multi-tenant system on Kotlin and MongoDB responsible for
generating compensation statements for enterprise customers during salary
planning cycles. The system needed to produce 5,000 PDF statements in under
25 seconds for a single customer's planning cycle, while maintaining strict
tenant data isolation across 5,000+ enterprise customers sharing the
platform. This wasn't pure accounting infrastructure in the GL sense, but it
sat directly adjacent to compensation financial data — salary figures, bonus
calculations, statement generation that fed into customers' own payroll and
compensation review processes — so the design problems (tenant isolation,
data accuracy under scale, generating financially-sensitive documents
correctly the first time since they're distributed directly to employees)
were closely related to what this assessment is testing for.

Separately, I later led the architecture for the broader Compensation Next
Generation Salary Planning platform, modernizing it from a legacy monolith
into a microservices-based system on Spring Boot, PostgreSQL, and Google
Cloud Run — reducing release cycle time by 60% and serving the same
1M+-user, 5,000+-tenant base.

## A data integrity or reconciliation issue I identified and resolved

[This needs a real, specific example from your UKG experience — I don't have
a verified instance of this in what we've discussed, and I don't want to
invent one for a financial-systems assessment, where specificity matters a
lot if you're asked to elaborate live. Did you encounter a case where
compensation data, payroll calculations, or tenant-scoped data became
inconsistent — between a cache and source of truth, between two services
during a migration, or after an MFA/auth change affected access to records?
Tell me what actually happened and I'll write this section properly.]

## An experience with period-end close, audit preparation, or compliance
requirements

The MFA migration for Kronos WFC is the strongest fit here, even though it's
security rather than accounting compliance specifically — eliminating a
critical OWASP A07 identity vulnerability across 5,000+ enterprise tenants
required the same kind of discipline this assessment is testing: documenting
exactly what changed, when, for which tenants, with a clear audit trail of
the rollout, and coordinating the change without breaking authenticated
access for live enterprise customers mid-migration. The broader RBAC/ABAC
framework I designed — Role Types as permission templates with
tenant-configured data scoping — is also directly relevant to "segregation
of duties," since the same model that prevented one tenant from seeing
another tenant's data is structurally the same problem as preventing one
role from doing something another role should exclusively own.

[If you have a more directly applicable example — actual SOC2 audit prep,
actual period-close process you supported, actual compliance documentation
work — that would be a stronger and more literal answer than the security
parallel above. Let me know if something comes to mind and I'll rewrite this
section to use it instead.]

## A challenging multi-tenant or multi-entity data modeling problem I solved

The Scoped RBAC framework is the clearest example. The challenge was
designing access governance for 1M+ users across 5,000+ enterprise tenants
where each tenant needed **independently configurable** permission
structures — not just "tenant A's data is isolated from tenant B's," which
is the easy part, but "tenant A wants Role X to mean something different in
terms of data scope than tenant B's Role X." I solved this by introducing
Role Types as reusable permission *templates*, with the actual data scoping
configured per tenant on top of the template — so the platform team
maintains one set of role definitions centrally, while each enterprise
customer configures their own scoping rules without needing platform
engineering involvement for every tenant-specific access policy. This is
structurally the same kind of problem as this assessment's
parent-company-with-subsidiaries requirement: a shared structural model
(Role Types here, Entity hierarchy in the AR module) with per-instance
configuration layered on top, rather than either a fully rigid shared schema
or a fully duplicated one-off schema per tenant.

---

## Time spent on this section: ~15 minutes (excluding the items flagged for your input)
