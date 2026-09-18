import { useAdminNotifyUsers } from "@/api/hooks/useAdminPlatformOverview";
import type { NotificationPriority, UserRole } from "@/api/types";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    Button,
    Checkbox,
    Form,
    FormGroup,
    InlineLoading,
    Select,
    SelectItem,
    TextArea,
    TextInput,
    Tile,
    ToastNotification
} from "@carbon/react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import styles from "./NotifyUsersPage.module.scss";
import { z } from "zod";

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

const schema = z.object({
    topicName: z.string()
        .trim()
        .min(1, "Topic name is required")
        .max(160, "Topic name cannot be more than 160 characters"),
    message: z.string()
        .trim()
        .min(1, "Notification description is required")
        .max(4000, "Notification description cannot be more than 4000 characters"),
    roles: z.array(z.enum([
        "LEARNER",
        "SCENARIO_AUTHOR",
        "REVIEWER",
        "ADMINISTRATOR"
    ])).min(1, "Select at least one audience"),
    priority: z.enum(["NORMAL", "IMPORTANT", "CRITICAL"])
});

type FormValues = z.infer<typeof schema>;

const DEFAULT_VALUES: FormValues = {
    topicName: "",
    message: "",
    roles: [],
    priority: "NORMAL"
};


export default function NotifyUsersPage() {
    const [showSuccessToast, setShowSuccessToast] = useState(false);
    const [showFailureToast, setShowFailureToast] = useState(false);
    const notifyUsers = useAdminNotifyUsers();

    const {
        register,
        handleSubmit,
        reset,
        setValue,
        watch,
        formState: { errors }
    } = useForm<FormValues>({
        resolver: zodResolver(schema),
        defaultValues: DEFAULT_VALUES
    });

    const message = watch("message");
    const selectedRoles = watch("roles");
    const priority = watch("priority") as NotificationPriority;

    const handleRoleChange = (
        role: UserRole,
        checked: boolean
    ) => {
        const roles = checked
            ? [...selectedRoles, role]
            : selectedRoles.filter((selectedRole) => selectedRole !== role);

        setValue("roles", [...new Set(roles)], {
            shouldDirty: true,
            shouldValidate: true
        });
    };

    const submitNotification = (values: FormValues) => {
        notifyUsers.mutate(values, {
            onSuccess: () => {
                reset(DEFAULT_VALUES);

                setShowSuccessToast(true);

                setTimeout(() => {
                    setShowSuccessToast(false);
                }, 4000);
            },
            onError: () => {
                triggerFailureToast();
            }
        });
    };

    const triggerFailureToast = () => {
        setShowFailureToast(true);

        setTimeout(() => {
            setShowFailureToast(false);
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
                        onSubmit={handleSubmit(submitNotification, triggerFailureToast)}
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
                                disabled={notifyUsers.isPending}
                                invalid={Boolean(errors.topicName)}
                                invalidText={errors.topicName?.message}
                                maxLength={160}
                                {...register("topicName")}
                            />

                            <TextArea
                                id="notification-description"
                                labelText="Message"
                                helperText={`${message.length}/4000 characters`}
                                disabled={notifyUsers.isPending}
                                invalid={Boolean(errors.message)}
                                invalidText={errors.message?.message}
                                maxLength={4000}
                                rows={6}
                                {...register("message")}
                            />

                            <Select
                                id="notification-priority"
                                labelText="Priority"
                                helperText="Choose the delivery urgency that matches the message"
                                disabled={notifyUsers.isPending}
                                {...register("priority")}
                            >
                                <SelectItem value="NORMAL" text="Normal" />
                                <SelectItem value="IMPORTANT" text="Important" />
                                <SelectItem value="CRITICAL" text="Critical" />
                            </Select>

                            {priority === "IMPORTANT" && (
                                <p className={styles.priorityNotice} role="status">
                                    Important notifications are expedited for major announcements,
                                    deadlines, and newly published courses.
                                </p>
                            )}

                            {priority === "CRITICAL" && (
                                <p className={styles.priorityWarning} role="status">
                                    Critical notifications are expedited across eligible event streams.
                                    Use them only for urgent or time-sensitive messages.
                                </p>
                            )}
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
                                                checked={selectedRoles.includes(role)}
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

                                {errors.roles?.message && (
                                    <p
                                        className={styles.error}
                                        role="alert"
                                    >
                                        {errors.roles.message}
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
