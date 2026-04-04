import type { Dispatch, SetStateAction } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import type { ScoreEditState } from "@/lib/app-types";
import { useTranslation } from "react-i18next";

type ScoreEditDialogProps = {
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>;
  scoreEdit: ScoreEditState | null;
  setScoreEdit: Dispatch<SetStateAction<ScoreEditState | null>>;
  onSave: () => void;
};

export function ScoreEditDialog(props: ScoreEditDialogProps) {
  const { open, setOpen, scoreEdit, setScoreEdit, onSave } = props;
  const { t } = useTranslation("scores");

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t("editTitle")}</DialogTitle>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="score-edit-achievements">achievements</FieldLabel>
            <Input
              id="score-edit-achievements"
              value={scoreEdit?.achievements ?? ""}
              onChange={(event) =>
                setScoreEdit((previous) => (previous ? { ...previous, achievements: event.target.value } : previous))
              }
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="score-edit-rank">rank</FieldLabel>
            <Input
              id="score-edit-rank"
              value={scoreEdit?.rank ?? ""}
              onChange={(event) => setScoreEdit((previous) => (previous ? { ...previous, rank: event.target.value } : previous))}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="score-edit-dx">dxScore</FieldLabel>
            <Input
              id="score-edit-dx"
              value={scoreEdit?.dxScore ?? ""}
              onChange={(event) =>
                setScoreEdit((previous) => (previous ? { ...previous, dxScore: event.target.value } : previous))
              }
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="score-edit-fc">fc</FieldLabel>
            <Input
              id="score-edit-fc"
              value={scoreEdit?.fc ?? ""}
              onChange={(event) => setScoreEdit((previous) => (previous ? { ...previous, fc: event.target.value } : previous))}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="score-edit-fs">fs</FieldLabel>
            <Input
              id="score-edit-fs"
              value={scoreEdit?.fs ?? ""}
              onChange={(event) => setScoreEdit((previous) => (previous ? { ...previous, fs: event.target.value } : previous))}
            />
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t("editBtnCancel")}
          </Button>
          <Button onClick={onSave}>{t("editBtnSave")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
