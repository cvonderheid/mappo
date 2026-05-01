import { FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type {
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
} from "@/lib/types";

const DEFAULT_REPO = "";
const DEFAULT_PATH = "releases/releases.manifest.json";
const DEFAULT_REF = "main";

type ReleaseIngestDrawerProps = {
  defaultProjectId?: string;
  isSubmitting: boolean;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  onIngest: (request?: ReleaseManifestIngestRequest) => Promise<void>;
  showTrigger?: boolean;
};

export default function ReleaseIngestDrawer({
  defaultProjectId,
  isSubmitting,
  open,
  onOpenChange,
  onIngest,
  showTrigger = true,
}: ReleaseIngestDrawerProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const [repo, setRepo] = useState(DEFAULT_REPO);
  const [path, setPath] = useState(DEFAULT_PATH);
  const [ref, setRef] = useState(DEFAULT_REF);
  const [duplicateMode, setDuplicateMode] = useState<"skip" | "allow">("skip");
  const isOpen = open ?? internalOpen;

  function setDrawerOpen(nextOpen: boolean): void {
    if (onOpenChange) {
      onOpenChange(nextOpen);
      return;
    }
    setInternalOpen(nextOpen);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    await onIngest({
      projectId: defaultProjectId,
      repo: repo.trim(),
      path: path.trim(),
      ref: ref.trim(),
      allowDuplicates: duplicateMode === "allow",
    });
    setDrawerOpen(false);
  }

  return (
    <Drawer direction="top" open={isOpen} onOpenChange={setDrawerOpen}>
      {showTrigger ? (
        <DrawerTrigger asChild>
          <Button type="button" variant="outline">
            Advanced release import
          </Button>
        </DrawerTrigger>
      ) : null}
      <DrawerContent className="glass-card">
        <DrawerHeader>
          <DrawerTitle>Advanced release import</DrawerTitle>
          <DrawerDescription>
            Use this only when you need to override the default release source settings for a one-off import.
          </DrawerDescription>
        </DrawerHeader>
        <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
          <form
            id="release-ingest-form"
            className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
            onSubmit={handleSubmit}
          >
            <div className="space-y-1 lg:col-span-2">
              <Label htmlFor="release-ingest-repo">GitHub repo</Label>
              <Input
                id="release-ingest-repo"
                value={repo}
                onChange={(event) => setRepo(event.target.value)}
                placeholder="owner/repo"
                required
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="release-ingest-ref">Git ref</Label>
              <Input
                id="release-ingest-ref"
                value={ref}
                onChange={(event) => setRef(event.target.value)}
                placeholder="main"
                required
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="release-ingest-duplicates">Duplicate handling</Label>
              <Select value={duplicateMode} onValueChange={(value) => setDuplicateMode(value as "skip" | "allow")}>
                <SelectTrigger id="release-ingest-duplicates" className="h-10 w-full bg-background/90 text-sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="skip">Skip existing releases</SelectItem>
                  <SelectItem value="allow">Allow duplicate ingest</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1 lg:col-span-4">
              <Label htmlFor="release-ingest-path">Manifest path</Label>
              <Input
                id="release-ingest-path"
                value={path}
                onChange={(event) => setPath(event.target.value)}
                placeholder="releases/releases.manifest.json"
                required
              />
            </div>
          </form>
        </div>
        <DrawerFooter className="border-t border-border/70">
          <DrawerClose asChild>
            <Button type="button" variant="outline">
              Close
            </Button>
          </DrawerClose>
          <Button type="submit" form="release-ingest-form" disabled={isSubmitting}>
            {isSubmitting ? "Importing..." : "Import releases"}
          </Button>
        </DrawerFooter>
      </DrawerContent>
    </Drawer>
  );
}
