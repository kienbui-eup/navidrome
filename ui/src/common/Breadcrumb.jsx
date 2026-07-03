import React from 'react'
import PropTypes from 'prop-types'
import { Link } from 'react-router-dom'
import MuiBreadcrumbs from '@material-ui/core/Breadcrumbs'
import Typography from '@material-ui/core/Typography'
import NavigateNextIcon from '@material-ui/icons/NavigateNext'
import { makeStyles } from '@material-ui/core/styles'

// Breadcrumb trail for detail pages (album / artist). The last item has no `to`
// and represents the current page; it is marked aria-current="page" and is not
// a link.
const useStyles = makeStyles(
  (theme) => ({
    root: {
      fontSize: '0.8125rem',
      margin: theme.spacing(1, 0),
    },
    link: {
      color: theme.palette.text.secondary,
      textDecoration: 'none',
      '&:hover': {
        textDecoration: 'underline',
      },
    },
    current: {
      fontSize: '0.8125rem',
      fontWeight: 600,
      color: theme.palette.text.primary,
    },
  }),
  { name: 'NDBreadcrumb' },
)

export const Breadcrumb = ({ items }) => {
  const classes = useStyles()
  return (
    <MuiBreadcrumbs
      aria-label="breadcrumb"
      separator={<NavigateNextIcon fontSize="small" />}
      className={classes.root}
    >
      {items.map((item, index) => {
        const isLast = index === items.length - 1
        if (item.to && !isLast) {
          return (
            <Link key={index} to={item.to} className={classes.link}>
              {item.label}
            </Link>
          )
        }
        return (
          <Typography
            key={index}
            className={classes.current}
            aria-current="page"
            component="span"
          >
            {item.label}
          </Typography>
        )
      })}
    </MuiBreadcrumbs>
  )
}

Breadcrumb.propTypes = {
  items: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.node.isRequired,
      to: PropTypes.string,
    }),
  ).isRequired,
}

export default Breadcrumb
