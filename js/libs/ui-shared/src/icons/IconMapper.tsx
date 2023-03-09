import {
  BitbucketIcon,
  CubeIcon,
  FacebookSquareIcon,
  GithubIcon,
  GitlabIcon,
  GoogleIcon,
  InstagramIcon,
  LinkedinIcon,
  MicrosoftIcon,
  OpenshiftIcon,
  PaypalIcon,
  StackOverflowIcon,
  TwitterIcon,
} from "@patternfly/react-icons";

type IconMapperProps = {
  icon: string;
};

export const IconMapper = ({ icon }: IconMapperProps) => {
  const Icon = getIcon(icon);
  return <Icon size="lg" alt={icon} />;
};

function getIcon(icon: string) {
  switch (icon) {
    case "github":
      return GithubIcon;
    case "facebook":
      return FacebookSquareIcon;
    case "gitlab":
      return GitlabIcon;
    case "google":
      return GoogleIcon;
    case "linkedin":
      return LinkedinIcon;

    case "openshift-v3":
    case "openshift-v4":
      return OpenshiftIcon;
    case "stackoverflow":
      return StackOverflowIcon;
    case "twitter":
      return TwitterIcon;
    case "microsoft":
      return MicrosoftIcon;
    case "bitbucket":
      return BitbucketIcon;
    case "instagram":
      return InstagramIcon;
    case "paypal":
      return PaypalIcon;
    default:
      return CubeIcon;
  }
}
