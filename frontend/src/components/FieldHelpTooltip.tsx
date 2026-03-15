import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

type FieldHelpTooltipProps = {
  content: string;
};

export default function FieldHelpTooltip({ content }: FieldHelpTooltipProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-border/70 text-[10px] font-semibold text-muted-foreground hover:text-foreground"
          aria-label="Field help"
        >
          ?
        </button>
      </TooltipTrigger>
      <TooltipContent>{content}</TooltipContent>
    </Tooltip>
  );
}
