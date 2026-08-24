import { useAdminNotifyUsers } from "@/api/hooks/useAdminPlatformOverview";
import { AdminNotificationRequest, UserRole } from "@/api/types";
import {
    Button,
    Checkbox,
    Form,
    FormGroup,
    InlineLoading,
    TextArea,
    TextInput,
    Tile,
    ToastNotification
} from "@carbon/react";
import { useState } from "react";
import styles from "./NotifyUsersPage.module.scss";

const ROLES: UserRole[] = [
    "LEARNER",
    "SCENARIO_AUTHOR",
    "REVIEWER",
    "ADMINISTRATOR"
];

const ROLE_LABELS: Record<UserRole, string> = {
    LEARNER: "Learners",
    SCENARIO_AUTHOR: "Scenario authors",
    REVIEWER: "Reviewers",
    ADMINISTRATOR: "Administrators"
};

export default function NotifyUsersPage() {
    const [showSuccessToast, setShowSuccessToast] = useState(false);
    const [showFailureToast, setShowFailureToast] = useState(false);
    const notifyUsers = useAdminNotifyUsers();

    const [form, setForm] = useState<AdminNotificationRequest>({
        topicName: "",
        notificationDescription: "",
        roles: []
    });

    const [errors, setErrors] = useState({
        topicName: "",
        notificationDescription: "",
        roles: ""
    });

    const emptyForm = () => {
        setForm({
            topicName: "",
            notificationDescription: "",
            roles: []
        });

        setErrors({
            topicName: "",
            notificationDescription: "",
            roles: ""
        });
    };

    const validateForm = () => {
        const newErrors = {
            topicName: "",
            notificationDescription: "",
            roles: ""
        };

        if (!form.topicName.trim()) {
            newErrors.topicName = "Topic name is required";
        } else if (form.topicName.length > 160) {
            newErrors.topicName =
                "Topic name cannot be more than 160 characters";
        }

        if (!form.notificationDescription.trim()) {
            newErrors.notificationDescription =
                "Notification description is required";
        } else if (form.notificationDescription.length > 4000) {
            newErrors.notificationDescription =
                "Notification description cannot be more than 4000 characters";
        }

        if (form.roles.length === 0) {
            newErrors.roles = "Select at least one audience";
        }

        setErrors(newErrors);

        return !Object.values(newErrors).some(Boolean);
    };

    const handleRoleChange = (
        role: UserRole,
        checked: boolean
    ) => {
        setForm((current) => ({
            ...current,
            roles: checked
                ? [...current.roles, role]
                : current.roles.filter((r) => r !== role)
        }));

        if (checked) {
            setErrors((current) => ({
                ...current,
                roles: ""
            }));
        }
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        if (!validateForm()) {
            setShowFailureToast(true);

            setTimeout(() => {
                setShowFailureToast(false);
            }, 4000);
            return;
        }

        notifyUsers.mutate(form, {
            onSuccess: () => {
                emptyForm();
            }
        });

        setShowSuccessToast(true);

        setTimeout(() => {
            setShowSuccessToast(false);
        }, 4000);
    };

    return (
        <div>
            {showSuccessToast && (
                <div className={styles.toastContainer}>
                    <ToastNotification
                        kind="success"
                        title="Notification published"
                        subtitle="The notification was successfully published to the selected audiences."
                        onClose={() => setShowSuccessToast(false)}
                    />
                </div>
            )}

            {showFailureToast && (
                <div className={styles.toastContainer}>
                    <ToastNotification
                        kind="error"
                        title="Notification failed to publish"
                        subtitle="The notification failed to be published to the selected audiences."
                        onClose={() => setShowFailureToast(false)}
                    />
                </div>
            )}
            <main className={styles.page}>
                <div className={styles.pageHeader}>
                    <h1>Notify users</h1>
                    <p>
                        Send a platform notification to one or more user groups.
                    </p>
                </div>

                <Tile className={styles.notificationCard}>
                    <Form
                        onSubmit={handleSubmit}
                        className={styles.form}
                    >
                        <div className={styles.section}>
                            <div className={styles.sectionHeader}>
                                <h2>Notification details</h2>
                                <p>
                                    Enter the message that will be sent to users.
                                </p>
                            </div>

                            <TextInput
                                id="topic-name"
                                labelText="Topic name"
                                helperText="A short title for the notification"
                                value={form.topicName}
                                disabled={notifyUsers.isPending}
                                invalid={Boolean(errors.topicName)}
                                invalidText={errors.topicName}
                                maxLength={160}
                                onChange={(e) => {
                                    setForm((current) => ({
                                        ...current,
                                        topicName: e.target.value
                                    }));

                                    setErrors((current) => ({
                                        ...current,
                                        topicName: ""
                                    }));
                                }}
                            />

                            <TextArea
                                id="notification-description"
                                labelText="Message"
                                helperText={`${form.notificationDescription.length}/4000 characters`}
                                value={form.notificationDescription}
                                disabled={notifyUsers.isPending}
                                invalid={Boolean(
                                    errors.notificationDescription
                                )}
                                invalidText={
                                    errors.notificationDescription
                                }
                                maxLength={4000}
                                rows={6}
                                onChange={(e) => {
                                    setForm((current) => ({
                                        ...current,
                                        notificationDescription:
                                            e.target.value
                                    }));

                                    setErrors((current) => ({
                                        ...current,
                                        notificationDescription: ""
                                    }));
                                }}
                            />
                        </div>

                        <div className={styles.divider} />

                        <div className={styles.section}>
                            <FormGroup
                                legendText="Audience"
                                className={styles.roleGroup}
                            >
                                <p className={styles.sectionDescription}>
                                    Select the groups that should receive this
                                    notification.
                                </p>

                                <div className={styles.rolesGrid}>
                                    {ROLES.map((role) => (
                                        <div
                                            className={styles.roleOption}
                                            key={role}
                                        >
                                            <Checkbox
                                                id={`role-${role}`}
                                                labelText={ROLE_LABELS[role]}
                                                checked={form.roles.includes(role)}
                                                disabled={
                                                    notifyUsers.isPending
                                                }
                                                onChange={(
                                                    _,
                                                    {
                                                        checked
                                                    }: {
                                                        checked: boolean;
                                                    }
                                                ) =>
                                                    handleRoleChange(
                                                        role,
                                                        checked
                                                    )
                                                }
                                            />
                                        </div>
                                    ))}
                                </div>

                                {errors.roles && (
                                    <p
                                        className={styles.error}
                                        role="alert"
                                    >
                                        {errors.roles}
                                    </p>
                                )}
                            </FormGroup>
                        </div>

                        <div className={styles.actions}>
                            <Button
                                type="submit"
                                disabled={notifyUsers.isPending}
                            >
                                Send notification
                            </Button>

                            {notifyUsers.isPending && (
                                <InlineLoading
                                    description="Sending notification..."
                                />
                            )}
                        </div>
                    </Form>
                </Tile>
            </main>
        </div>
    );
}